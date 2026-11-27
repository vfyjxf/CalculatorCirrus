package dev.vfyjxf.cirrus.planning.core.verify;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.core.solve.TransactionalLedger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * M1.2 / M7.2 Independent Forward Replay Verifier for Event Witnesses (Pure JDK 21).
 *
 * <p>Reconstructs full prefix conservation, strict two-pool FIFO lot provenance, per-batch homogeneous
 * slot enforcement, non-consumable lock exclusivity, machine coverage, target delivery, and Farkas
 * non-lossy integer certificate from scratch — never trusting planner-internal booleans.
 */
public final class TwoPoolReplayVerifier {

    public sealed interface ReplayEvent permits
            PullStockEvent,
            ExternalSupplyEvent,
            InstallMachineEvent,
            CraftActionEvent,
            DeliverTargetEvent {}

    public record PullStockEvent(InventoryTier tier, ResourceKey resource, long amount) implements ReplayEvent {}

    public record ExternalSupplyEvent(ResourceKey resource, long guaranteedAmount, boolean forbidden) implements ReplayEvent {}

    public record InstallMachineEvent(
        String machineId,
        Map<ResourceKey, Long> consumedMaterials,
        @Nullable String requiredUpstreamMachineOrNull
    ) implements ReplayEvent {}

    public record BatchSlotBinding(
        int slotIndex,
        long amountPerBatch,
        boolean requireHomogeneousPerBatch,
        Map<ResourceKey, Long> totalByMember,
        List<Map<ResourceKey, Long>> perBatchWitnessOrEmpty
    ) {}

    public record CraftActionEvent(
        String modeId,
        long batches,
        @Nullable String requiredMachineIdOrNull,
        boolean machineCovered,
        List<BatchSlotBinding> slotBindings,
        Map<ResourceKey, Long> lockedCatalysts,
        Map<ResourceKey, Long> guaranteedPrimaryOutputs,
        Map<ResourceKey, Long> guaranteedByproductOutputs
    ) implements ReplayEvent {}

    public record DeliverTargetEvent(ResourceKey resource, long amount) implements ReplayEvent {}

    public record ReplayResult(
        boolean valid,
        List<String> violations,
        Map<ResourceKey, Long> finalOrdinarySurplus,
        Map<ResourceKey, Long> finalByproductSurplus,
        boolean nonLossyCertified
    ) {}

    private TwoPoolReplayVerifier() {}

    public static ReplayResult replay(
        InventorySnapshot initialInventory,
        Set<String> initiallyOwnedMachines,
        Map<ResourceKey, Long> requiredTargets,
        List<ReplayEvent> events,
        boolean enforceNonLossyCertificate
    ) {
        Objects.requireNonNull(initialInventory, "initialInventory");
        Objects.requireNonNull(requiredTargets, "requiredTargets");
        Objects.requireNonNull(events, "events");

        TransactionalLedger ledger = new TransactionalLedger(initialInventory, initiallyOwnedMachines);
        List<String> violations = new ArrayList<>();
        Map<ResourceKey, Long> delivered = new TreeMap<>();
        List<NonLossyCertificateVerifier.NetColumn> netColumns = new ArrayList<>();

        for (int idx = 0; idx < events.size(); idx++) {
            int seq = ledger.advanceEventSeq();
            ReplayEvent ev = events.get(idx);
            try {
                switch (ev) {
                    case PullStockEvent pull -> {
                        if (pull.amount() <= 0L) {
                            violations.add("INVALID_PULL_AMOUNT@" + idx);
                        } else {
                            ledger.pullFromTier(pull.tier(), pull.resource(), pull.amount(), seq);
                        }
                    }
                    case ExternalSupplyEvent ext -> {
                        if (ext.forbidden()) {
                            violations.add("FORBIDDEN_EXTERNAL_SUPPLY@" + idx + ":" + ext.resource().shortString());
                        } else if (ext.guaranteedAmount() <= 0L) {
                            violations.add("ZERO_GUARANTEED_SUPPLY@" + idx + ":" + ext.resource().shortString());
                        } else {
                            ledger.publishLot(TransactionalLedger.PoolKind.ordinary, ext.resource(), seq, 0, ext.guaranteedAmount());
                        }
                    }
                    case InstallMachineEvent inst -> {
                        if (inst.requiredUpstreamMachineOrNull() != null
                                && !ledger.hasMachine(inst.requiredUpstreamMachineOrNull())) {
                            violations.add("MISSING_UPSTREAM_MACHINE@" + idx + ":" + inst.requiredUpstreamMachineOrNull());
                            break;
                        }
                        for (var e : inst.consumedMaterials().entrySet()) {
                            if (e.getValue() > 0L) {
                                ledger.consumeFifo(e.getKey(), e.getValue(), seq);
                            }
                        }
                        ledger.installMachine(inst.machineId());
                        Map<String, Long> delta = new TreeMap<>();
                        for (var e : inst.consumedMaterials().entrySet()) {
                            delta.merge("R:" + e.getKey().canonicalString(), -e.getValue(), Math::addExact);
                        }
                        delta.put("M:" + inst.machineId(), 1L);
                        netColumns.add(new NonLossyCertificateVerifier.NetColumn("install:" + inst.machineId() + "@" + idx, delta));
                    }
                    case CraftActionEvent craft -> {
                        if (craft.batches() <= 0L) {
                            violations.add("NON_POSITIVE_BATCHES@" + idx);
                            break;
                        }
                        if (craft.requiredMachineIdOrNull() != null) {
                            if (!craft.machineCovered() || !ledger.hasMachine(craft.requiredMachineIdOrNull())) {
                                violations.add("UNCOVERED_OR_MISSING_MACHINE@" + idx + ":" + craft.requiredMachineIdOrNull());
                                break;
                            }
                        }
                        // Lock non-consumable catalysts BEFORE consuming inputs (proves lock exclusivity!).
                        for (var cat : craft.lockedCatalysts().entrySet()) {
                            ledger.lockCatalyst(cat.getKey(), cat.getValue());
                        }

                        Map<ResourceKey, Long> totalConsumed = new TreeMap<>();
                        boolean bindingOk = true;
                        for (BatchSlotBinding sb : craft.slotBindings()) {
                            long expectedTotal = Math.multiplyExact(sb.amountPerBatch(), craft.batches());
                            long actualSum = 0L;
                            for (var me : sb.totalByMember().entrySet()) {
                                if (me.getValue() < 0L) {
                                    violations.add("NEGATIVE_BINDING@" + idx);
                                    bindingOk = false;
                                }
                                if (sb.requireHomogeneousPerBatch() && (me.getValue() % sb.amountPerBatch() != 0L)) {
                                    violations.add("PER_BATCH_HOMOGENEITY_VIOLATED@" + idx + ":slot" + sb.slotIndex());
                                    bindingOk = false;
                                }
                                actualSum = Math.addExact(actualSum, me.getValue());
                                totalConsumed.merge(me.getKey(), me.getValue(), Math::addExact);
                            }
                            if (actualSum != expectedTotal) {
                                violations.add("SLOT_SUM_MISMATCH@" + idx + ":slot" + sb.slotIndex());
                                bindingOk = false;
                            }
                            if (sb.requireHomogeneousPerBatch() && !sb.perBatchWitnessOrEmpty().isEmpty()) {
                                if (sb.perBatchWitnessOrEmpty().size() != craft.batches()) {
                                    violations.add("MICRO_BATCH_COUNT_MISMATCH@" + idx);
                                    bindingOk = false;
                                } else {
                                    for (Map<ResourceKey, Long> mb : sb.perBatchWitnessOrEmpty()) {
                                        if (mb.size() != 1) {
                                            violations.add("MICRO_BATCH_MIXED_VARIANT@" + idx);
                                            bindingOk = false;
                                        }
                                    }
                                }
                            }
                        }
                        if (!bindingOk) {
                            for (var cat : craft.lockedCatalysts().entrySet()) {
                                ledger.unlockCatalyst(cat.getKey(), cat.getValue());
                            }
                            break;
                        }

                        // Consume all inputs strictly from lots finished BEFORE seq (prevents self-byproduct!).
                        for (var c : totalConsumed.entrySet()) {
                            if (c.getValue() > 0L) {
                                ledger.consumeFifo(c.getKey(), c.getValue(), seq);
                            }
                        }

                        // Release catalysts and publish completed outputs.
                        for (var cat : craft.lockedCatalysts().entrySet()) {
                            ledger.unlockCatalyst(cat.getKey(), cat.getValue());
                        }
                        int stream = 0;
                        Map<String, Long> netDelta = new TreeMap<>();
                        for (var c : totalConsumed.entrySet()) {
                            netDelta.merge("R:" + c.getKey().canonicalString(), -c.getValue(), Math::addExact);
                        }
                        for (var o : craft.guaranteedPrimaryOutputs().entrySet()) {
                            if (o.getValue() > 0L) {
                                ledger.publishLot(TransactionalLedger.PoolKind.ordinary, o.getKey(), seq, stream++, o.getValue());
                                netDelta.merge("R:" + o.getKey().canonicalString(), o.getValue(), Math::addExact);
                            }
                        }
                        for (var byp : craft.guaranteedByproductOutputs().entrySet()) {
                            if (byp.getValue() > 0L) {
                                ledger.publishLot(TransactionalLedger.PoolKind.byproduct, byp.getKey(), seq, stream++, byp.getValue());
                                netDelta.merge("R:" + byp.getKey().canonicalString(), byp.getValue(), Math::addExact);
                            }
                        }
                        netColumns.add(new NonLossyCertificateVerifier.NetColumn(craft.modeId() + "@" + idx, netDelta));
                    }
                    case DeliverTargetEvent del -> {
                        if (del.amount() <= 0L) {
                            violations.add("INVALID_DELIVERY_AMOUNT@" + idx);
                        } else {
                            ledger.consumeFifo(del.resource(), del.amount(), seq);
                            delivered.merge(del.resource(), del.amount(), Math::addExact);
                        }
                    }
                }
            } catch (RuntimeException ex) {
                violations.add("REPLAY_EXCEPTION@" + idx + ":" + ex.getMessage());
            }
        }

        // Verify all targets were fully delivered.
        for (var req : requiredTargets.entrySet()) {
            long got = delivered.getOrDefault(req.getKey(), 0L);
            if (got < req.getValue()) {
                violations.add("TARGET_SHORTAGE:" + req.getKey().shortString() + ":need=" + req.getValue() + ",got=" + got);
            }
        }

        // Verify global Farkas non-lossy integer certificate if enabled.
        boolean nonLossyOk = true;
        if (enforceNonLossyCertificate && !netColumns.isEmpty()) {
            var cert = NonLossyCertificateVerifier.certify(netColumns);
            nonLossyOk = cert.certified();
            if (!nonLossyOk) {
                violations.add("NON_LOSSY_CERT_FAILED:" + cert.statusCode() + ":" + cert.failingModeId());
            }
        }

        var finalCp = ledger.checkpoint();
        Map<ResourceKey, Long> ordSurplus = new TreeMap<>();
        for (var e : finalCp.ordinaryLots().entrySet()) {
            long sum = 0L;
            try {
                for (var l : e.getValue()) sum = Math.addExact(sum, l.remainingQuantity());
            } catch (ArithmeticException ex) {
                violations.add("ARITHMETIC_OVERFLOW_ORDINARY:" + e.getKey().shortString());
                sum = Long.MAX_VALUE;
            }
            if (sum > 0L) ordSurplus.put(e.getKey(), sum);
        }
        Map<ResourceKey, Long> bypSurplus = new TreeMap<>();
        for (var e : finalCp.byproductLots().entrySet()) {
            long sum = 0L;
            try {
                for (var l : e.getValue()) sum = Math.addExact(sum, l.remainingQuantity());
            } catch (ArithmeticException ex) {
                violations.add("ARITHMETIC_OVERFLOW_BYPRODUCT:" + e.getKey().shortString());
                sum = Long.MAX_VALUE;
            }
            if (sum > 0L) bypSurplus.put(e.getKey(), sum);
        }

        return new ReplayResult(
            violations.isEmpty(),
            List.copyOf(violations),
            ordSurplus,
            bypSurplus,
            nonLossyOk
        );
    }
}
