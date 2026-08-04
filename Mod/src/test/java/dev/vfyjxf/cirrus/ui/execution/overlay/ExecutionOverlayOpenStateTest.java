package dev.vfyjxf.cirrus.ui.execution.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayOpenStateTest {

	@Test
	void stateChangesNotifyRegisteredListeners() {
		ExecutionOverlayOpenState.setOpen(true);
		int[] calls = {0};
		Runnable listener = () -> calls[0]++;
		ExecutionOverlayOpenState.addListener(listener);
		try {
			assertFalse(ExecutionOverlayOpenState.setOpen(false));
			assertEquals(1, calls[0]);

			assertFalse(ExecutionOverlayOpenState.setOpen(false));
			assertEquals(1, calls[0]);

			assertTrue(ExecutionOverlayOpenState.toggle());
			assertEquals(2, calls[0]);

			ExecutionOverlayOpenState.removeListener(listener);
			ExecutionOverlayOpenState.setOpen(false);
			assertEquals(2, calls[0]);
		} finally {
			ExecutionOverlayOpenState.removeListener(listener);
			ExecutionOverlayOpenState.setOpen(true);
		}
	}
}
