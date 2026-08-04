package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.taffy.style.TrackSizingFunction;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayGrid;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gridAutoRows;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gridTemplateColumns;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

public class ExecutionSlotGridWidget extends CompositeWidget<ExecutionSlotWidget> {
	//region state

	private int columns;

	//endregion

	public ExecutionSlotGridWidget(int columns) {
		setColumns(columns);
	}

	public void setColumns(int columns) {
		this.columns = Math.max(1, columns);
		useStyle(
				displayGrid(),
				gridTemplateColumns(fixedTracks(this.columns)),
				gridAutoRows(TrackSizingFunction.fixed(ExecutionSlotWidget.slotSize)),
				widthOf(this.columns * ExecutionSlotWidget.slotSize),
				minWidth(this.columns * ExecutionSlotWidget.slotSize),
				minHeight(0)
		);
	}

	//region child

	public void setSlots(List<ExecutionSlotWidget> slots) {
		clear();
		for (ExecutionSlotWidget slot : slots) {
			addWidget(slot);
		}
	}

	//endregion

	private TrackSizingFunction[] fixedTracks(int count) {
		TrackSizingFunction[] tracks = new TrackSizingFunction[Math.max(1, count)];
		for (int index = 0; index < tracks.length; index++) {
			tracks[index] = TrackSizingFunction.fixed(ExecutionSlotWidget.slotSize);
		}
		return tracks;
	}
}
