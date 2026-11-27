package dev.vfyjxf.cirrus.ui.workbench.flow;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.LibFFI;
import org.lwjgl.system.macosx.ObjCRuntime;

import java.util.Locale;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Installs a defensive macOS Cocoa {@code magnifyWithEvent:} hook on the GLFW content view so trackpad pinch-to-zoom feeds DAG zoom natively.
 */
public final class MacTrackpadPinchBridge {

    private static final DoubleAdder pendingMagnification = new DoubleAdder();
    private static volatile boolean attempted;
    private static volatile boolean installed;
    private static volatile long hookedClass;
    private static volatile long previousImp;
    private static volatile long msgSend;
    private static volatile long selMagnification;
    private static @Nullable CallbackI strongCallbackRef;
    private static @Nullable FFICIF strongCifRef;

    private MacTrackpadPinchBridge() {}

    public static void ensureInstalled(long glfwWindow) {
        if (installed || attempted || glfwWindow == 0L || !isMacOs()) {
            return;
        }
        attempted = true;
        try {
            long nsWindow = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
            if (nsWindow == 0L) {
                return;
            }
            long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            if (send == 0L) {
                return;
            }
            long selContentView = ObjCRuntime.sel_registerName("contentView");
            long contentView = JNI.invokePPP(nsWindow, selContentView, send);
            if (contentView == 0L) {
                return;
            }
            long viewClass = ObjCRuntime.object_getClass(contentView);
            if (viewClass == 0L || viewClass == hookedClass) {
                return;
            }
            long selMagnify = ObjCRuntime.sel_registerName("magnifyWithEvent:");
            long selMag = ObjCRuntime.sel_registerName("magnification");
            msgSend = send;
            selMagnification = selMag;

            FFICIF cif = APIUtil.apiCreateCIF(
                LibFFI.FFI_DEFAULT_ABI,
                LibFFI.ffi_type_void,
                LibFFI.ffi_type_pointer,
                LibFFI.ffi_type_pointer,
                LibFFI.ffi_type_pointer
            );
            CallbackI callback = new CallbackI() {
                @Override
                public FFICIF getCallInterface() {
                    return cif;
                }

                @Override
                public void callback(long ret, long args) {
                    long self = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args));
                    long sel = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args + POINTER_SIZE));
                    long event = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args + 2L * POINTER_SIZE));
                    if (event != 0L && msgSend != 0L && selMagnification != 0L) {
                        double mag = JNI.invokePPD(event, selMagnification, msgSend);
                        if (Double.isFinite(mag) && Math.abs(mag) > 1e-6) {
                            pendingMagnification.add(mag);
                        }
                    }
                    long prev = previousImp;
                    if (prev != 0L) {
                        JNI.invokePPPV(self, sel, event, prev);
                    }
                }
            };
            strongCifRef = cif;
            strongCallbackRef = callback;
            long callbackAddr = callback.address();
            previousImp = ObjCRuntime.class_replaceMethod(viewClass, selMagnify, callbackAddr, "v@:@");
            hookedClass = viewClass;
            installed = true;
        } catch (Throwable ignored) {
            installed = false;
        }
    }

    public static double drainMagnification(long glfwWindow) {
        ensureInstalled(glfwWindow);
        if (!installed) {
            return 0.0;
        }
        return pendingMagnification.sumThenReset();
    }

    public static void clearPending() {
        pendingMagnification.reset();
    }

    private static boolean isMacOs() {
        try {
            return Minecraft.ON_OSX;
        } catch (Throwable ignored) {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        }
    }
}
