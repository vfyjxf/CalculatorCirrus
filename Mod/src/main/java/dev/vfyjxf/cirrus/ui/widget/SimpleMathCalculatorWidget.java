package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipLocator;
import dev.vfyjxf.taffy.style.TrackSizingFunction;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.LongConsumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.border;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayGrid;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gridAutoFlowRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gridTemplateColumns;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gridTemplateRows;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Popup calculator with basic arithmetic operations (+, -, *, /).
 */
public class SimpleMathCalculatorWidget extends CompositeWidget<Widget> {

	private static final int buttonSize = 28;
	private static final int gap = 4;
	private static final int innerPadding = 6;

	private StringBuilder currentInput = new StringBuilder("0");
	private long storedValue = 0;
	private char pendingOperation = 0;
	private boolean startNewInput = true;
	private String expressionText = "";
	// Used to avoid duplicate input from numpad: record last consumed char from key press
	private long lastConsumedNano = 0L;
	private int lastConsumedCodePoint = -1;


	private @Nullable LongConsumer onResultConfirmed;
	private @Nullable Runnable onClose;

	private final DisplayWidget displayWidget;

	public SimpleMathCalculatorWidget() {
		int contentWidth = 4 * buttonSize + 3 * gap;
		int contentHeight = 4 * buttonSize + 3 * gap + 30 + gap;
		int totalWidth = contentWidth + innerPadding * 2 + 16;
		int totalHeight = contentHeight + innerPadding * 2 + 16;

		setFocusable(true);
		useStyle(
				background(Background.frame),
				padding(8),
				flexColumn(),
				sizeOf(totalWidth, totalHeight)
		);

		var content = addWidget(new WidgetGroup<>());
		content.useStyle(
				flexColumn(),
				rowGap(gap),
				padding(innerPadding)
		);

		displayWidget = content.addWidget(new DisplayWidget(contentWidth));

		var buttonGrid = content.addWidget(new WidgetGroup<>());
		buttonGrid.useStyle(
				displayGrid(),
				gridTemplateColumns(
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize)
				),
				gridTemplateRows(
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize),
						TrackSizingFunction.fixed(buttonSize)
				),
				gap(gap),
				gridAutoFlowRow()
		);

		String[] buttons = {
				"7", "8", "9", "/",
				"4", "5", "6", "*",
				"1", "2", "3", "-",
				"0", "C", "=", "+"
		};
		for (String label : buttons) {
			buttonGrid.addWidget(createButton(label));
		}

		setupKeyboardInput();
		setClickGroup(this);
		onClickOutside((context) -> close());
	}

	public static SimpleMathCalculatorWidget create() {
		return new SimpleMathCalculatorWidget();
	}

	private CalcButton createButton(String label) {
		CalcButton button = new CalcButton(label, label.equals("=")
				? () -> {
			handleButtonPress("=");
		}
				: () -> handleButtonPress(label)
		);
		if (label.equals("=")) {
			button.tooltip().insert(CirrusLangs.Calc.setValue.component(), Tooltip.body, TooltipLocator.after(Tooltip.title));
		}
		return button;
	}

	private void setupKeyboardInput() {
		onKeyPressed((input, context) -> {
			if (!focused()) {
				return EventDispatch.pass;
			}
			int key = input.key().getValue();
			switch (key) {
				case GLFW.GLFW_KEY_ESCAPE -> {
					close();
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					handleButtonPress("=");
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (currentInput.length() > 1) {
						currentInput.deleteCharAt(currentInput.length() - 1);
					} else {
						currentInput = new StringBuilder("0");
					}
					displayWidget.updateDisplay(expressionText, currentInput.toString());
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_0 -> {
					handleButtonPress("0");
					lastConsumed(codePointFromChar('0'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_1 -> {
					handleButtonPress("1");
					lastConsumed(codePointFromChar('1'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_2 -> {
					handleButtonPress("2");
					lastConsumed(codePointFromChar('2'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_3 -> {
					handleButtonPress("3");
					lastConsumed(codePointFromChar('3'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_4 -> {
					handleButtonPress("4");
					lastConsumed(codePointFromChar('4'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_5 -> {
					handleButtonPress("5");
					lastConsumed(codePointFromChar('5'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_6 -> {
					handleButtonPress("6");
					lastConsumed(codePointFromChar('6'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_7 -> {
					handleButtonPress("7");
					lastConsumed(codePointFromChar('7'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_8 -> {
					handleButtonPress("8");
					lastConsumed(codePointFromChar('8'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_9 -> {
					handleButtonPress("9");
					lastConsumed(codePointFromChar('9'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_ADD -> {
					handleButtonPress("+");
					lastConsumed(codePointFromChar('+'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_SUBTRACT -> {
					handleButtonPress("-");
					lastConsumed(codePointFromChar('-'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_MULTIPLY -> {
					handleButtonPress("*");
					lastConsumed(codePointFromChar('*'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_DIVIDE -> {
					handleButtonPress("/");
					lastConsumed(codePointFromChar('/'));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_EQUAL -> {
					handleButtonPress("=");
					lastConsumed(codePointFromChar('='));
					return EventDispatch.consumed;
				}
				case GLFW.GLFW_KEY_KP_DECIMAL -> {
					lastConsumed(codePointFromChar('.'));
					return EventDispatch.consumed;
				}
			}
			return EventDispatch.pass;
		});

		onCharTyped((codePoint, modifiers, context) -> {
			if (!focused()) {
				return EventDispatch.pass;
			}
			// If this char was already consumed via keypad key press, ignore it.
			long now = System.nanoTime();
			if (codePoint == lastConsumedCodePoint && now - lastConsumedNano < 50_000_000L) {
				// swallow duplicate char
				lastConsumedCodePoint = -1;
				return EventDispatch.consumed;
			}
			char c = (char) codePoint;
			if (Character.isDigit(c)) {
				handleButtonPress(String.valueOf(c));
				return EventDispatch.consumed;
			}
			switch (c) {
				case '+', '-', '*', '/', '=' -> {
					handleButtonPress(String.valueOf(c));
					return EventDispatch.consumed;
				}
				case 'c', 'C' -> {
					handleButtonPress("C");
					return EventDispatch.consumed;
				}
			}
			return EventDispatch.pass;
		});
	}

	private void handleButtonPress(String button) {
		switch (button) {
			case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
				if (startNewInput) {
					currentInput = new StringBuilder(button);
					startNewInput = false;
				} else {
					if (currentInput.toString().equals("0")) {
						currentInput = new StringBuilder(button);
					} else {
						currentInput.append(button);
					}
				}
				displayWidget.updateDisplay(expressionText, currentInput.toString());
			}
			case "+", "-", "*", "/" -> {
				calculatePending();
				pendingOperation = button.charAt(0);
				expressionText = storedValue + " " + pendingOperation + " ";
				startNewInput = true;
				displayWidget.updateDisplay(expressionText, "");
			}
			case "=" -> {
				calculatePending();
				expressionText = "";
				pendingOperation = 0;
				startNewInput = true;
				confirmResult();
			}
			case "C" -> {
				currentInput = new StringBuilder("0");
				storedValue = 0;
				pendingOperation = 0;
				startNewInput = true;
				expressionText = "";
				displayWidget.updateDisplay("", "0");
			}
		}
	}

	private void lastConsumed(int codePoint) {
		this.lastConsumedCodePoint = codePoint;
		this.lastConsumedNano = System.nanoTime();
	}

	private int codePointFromChar(char c) {
		return (int) c;
	}

	private void calculatePending() {
		long currentValue = parseCurrentInput();
		if (pendingOperation != 0) {
			switch (pendingOperation) {
				case '+' -> storedValue = storedValue + currentValue;
				case '-' -> storedValue = storedValue - currentValue;
				case '*' -> storedValue = storedValue * currentValue;
				case '/' -> {
					if (currentValue != 0) {
						storedValue = storedValue / currentValue;
					}
				}
			}
			currentInput = new StringBuilder(String.valueOf(storedValue));
			displayWidget.updateDisplay("", currentInput.toString());
		} else {
			storedValue = currentValue;
		}
	}

	private long parseCurrentInput() {
		try {
			return Long.parseLong(currentInput.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void confirmResult() {
		long result = parseCurrentInput();
		if (onResultConfirmed != null) {
			onResultConfirmed.accept(result);
		}
	}

	private void close() {
		if (onClose != null) {
			onClose.run();
		}
	}


	public long getCurrentValue() {
		return parseCurrentInput();
	}

	public SimpleMathCalculatorWidget setInitialValue(long value) {
		this.storedValue = value;
		this.currentInput = new StringBuilder(String.valueOf(value));
		this.displayWidget.updateDisplay("", currentInput.toString());
		this.startNewInput = true;
		return this;
	}

	public SimpleMathCalculatorWidget onResultConfirmed(LongConsumer callback) {
		this.onResultConfirmed = callback;
		return this;
	}

	public SimpleMathCalculatorWidget onClose(Runnable callback) {
		this.onClose = callback;
		return this;
	}

	private static class DisplayWidget extends Widget {
		private String expressionLine = "";
		private String valueLine = "0";

		DisplayWidget(int contentWidth) {
			useStyle(
					background(Background.dark),
					border(1),
					sizeOf(contentWidth, 30)
			);
		}

		void updateDisplay(String expression, String value) {
			this.expressionLine = expression;
			this.valueLine = value;
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			super.renderInternal(canvas, mouseX, mouseY, partialTicks);
			var font = context().font();
			int padding = 4;

			if (!expressionLine.isEmpty()) {
				int exprWidth = font.width(expressionLine);
				int exprX = width() - exprWidth - padding;
				canvas.text(expressionLine, exprX, 2, 0xAAAAAA, false);
			}

			String display = valueLine.isEmpty() ? "0" : valueLine;
			int textWidth = font.width(display);
			int textX = width() - textWidth - padding;
			int textY = expressionLine.isEmpty()
					? (height() - font.lineHeight) / 2
					: height() - font.lineHeight - 2;
			canvas.text(display, textX, textY, 0xFFFFFF, false);
		}
	}

	private static class CalcButton extends Widget {
		private final String label;

		CalcButton(String label, Runnable onClick) {
			this.label = label;
			useStyle(
					sizeOf(buttonSize, buttonSize),
					background(Background.Border.light)
			);

			onMouseClick((input, clickCount, context) -> {
				onClick.run();
				return EventDispatch.consumed;
			});
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			super.renderInternal(canvas, mouseX, mouseY, partialTicks);

			var font = context().font();
			int textColor = isOperator() ? 0xFFFF88 : 0xFFFFFF;
			if (hovered()) {
				textColor = 0xFFFFFF;
			}

			int textWidth = font.width(label);
			int textX = (width() - textWidth) / 2;
			int textY = (height() - font.lineHeight) / 2;

			canvas.text(label, textX, textY, textColor, false);
		}

		private boolean isOperator() {
			return switch (label) {
				case "+", "-", "*", "/", "=", "C" -> true;
				default -> false;
			};
		}
	}

}
