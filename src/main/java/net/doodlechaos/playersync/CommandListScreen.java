package net.doodlechaos.playersync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

/**
 * A custom list screen using Forge/NeoForge classes.
 */
public class CommandListScreen extends Screen {

    private final Minecraft minecraft;
    private final List<String> items;      // The data source for our list
    private MyListWidget listWidget;       // The custom list widget
    private Button addButton;
    private Button doneButton;

    /**
     * Basic constructor. Expects a reference to the Minecraft instance and some initial items.
     */
    public CommandListScreen(Minecraft minecraft, int frameNum, List<String> initialItems) {
        super(Component.literal("FRAME " + frameNum + " Commands"));
        this.minecraft = minecraft;
        this.items = initialItems;
    }

    @Override
    protected void init() {
        super.init();

        // Create the custom list widget (ObjectSelectionList).
        // Args: Minecraft instance, width, height, top, bottom, and itemHeight
        this.listWidget = new MyListWidget(this.minecraft, this.width, this.height, 40, this.height - 40, 24);

        // Populate the list with initial items.
        for (String s : items) {
            this.listWidget.addCustomEntry(this.listWidget.new MyListWidgetEntry(s));
        }

        // Add the list widget to the screen’s children and as a selectable widget.
        this.addWidget(this.listWidget);

        // "Add" button: creates a new entry with some default text
        this.addButton = Button.builder(Component.literal("Add"), button -> {
            this.listWidget.addCustomEntry(this.listWidget.new MyListWidgetEntry("New Entry"));
        }).bounds(this.width / 2 - 50, this.height - 28, 40, 20).build();
        this.addRenderableWidget(this.addButton);

        // "Done" button: saves changes and closes the screen
        this.doneButton = Button.builder(Component.literal("Done"), button -> {
            this.items.clear();
            this.items.addAll(this.listWidget.getStrings());
            this.onClose();
            PlayerSync.OpenKeyCommandsEditScreen = false;  // If you have a toggle in PlayerSync
        }).bounds(this.width / 2 + 10, this.height - 28, 40, 20).build();
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.listWidget.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Draw title at the top
        int textWidth = this.font.width(this.title.getString());
        guiGraphics.drawString(this.font, this.title.getString(),
                (this.width - textWidth) / 2, 10, 0xFFFFFF, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        // Make sure we copy items from listWidget into the items list
        this.items.clear();
        this.items.addAll(this.listWidget.getStrings());
        super.onClose();
    }

    //------------------------------------------------------------------------------
    // Inner class: Our custom ObjectSelectionList (replaces Fabric EntryListWidget)
    //------------------------------------------------------------------------------

    public class MyListWidget extends ObjectSelectionList<MyListWidget.MyListWidgetEntry> {

        public MyListWidget(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
            super(minecraft, width, /* height = */ (bottom - top), /* y = */ top, itemHeight);

        }

        // Public method to add entries (since addEntry is protected)
        public void addCustomEntry(MyListWidgetEntry entry) {
            this.addEntry(entry);
        }

        // Returns the current list of strings from all entries
        public List<String> getStrings() {
            List<String> result = new ArrayList<>();
            for (MyListWidgetEntry entry : this.children()) {
                result.add(entry.getText());
            }
            return result;
        }


        @Override
        protected int getScrollbarPosition() {
            // Position the scrollbar near the right edge
            return CommandListScreen.this.width - 10;
        }

        @Override
        public int getRowWidth() {
            // Use nearly the full screen width minus some padding
            return CommandListScreen.this.width - 20;
        }

        //------------------------------------------------------------------------------
        // Inner class: Each list entry (similar to EntryListWidget.Entry in Fabric)
        //------------------------------------------------------------------------------

        public class MyListWidgetEntry extends ObjectSelectionList.Entry<MyListWidgetEntry> {

            private EditBox textField;
            private Button removeButton;
            private Button upButton;
            private Button downButton;
            private final int ENTRY_HEIGHT = 24;
            private String initialText;

            public MyListWidgetEntry(String initialText) {
                this.initialText = initialText;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {

                // 1) Draw the index (1-based) in front
                int number = index + 1;
                String numberString = String.valueOf(number);
                int numberX = left + 2;
                int numberY = top + (ENTRY_HEIGHT - CommandListScreen.this.font.lineHeight) / 2;
                guiGraphics.drawString(CommandListScreen.this.font, numberString, numberX, numberY, 0xFFFFFF, false);

                // Some spacing after the number
                final int numberWidth = 20;
                int offsetX = left + numberWidth;

                // 2) Remove button [X]
                if (this.removeButton == null) {
                    this.removeButton = Button.builder(Component.literal("X"), btn -> {
                        MyListWidget.this.removeEntry(this);
                    }).bounds(offsetX + 5, top, 20, 20).build();
                } else {
                    this.removeButton.setX(offsetX + 5);
                    this.removeButton.setY(top);
                }
                this.removeButton.render(guiGraphics, mouseX, mouseY, partialTick);

                // 3) Up button [^]
                if (this.upButton == null) {
                    this.upButton = Button.builder(Component.literal("^"), btn -> moveUp())
                            .bounds(offsetX + 30, top, 20, 20).build();
                } else {
                    this.upButton.setX(offsetX + 30);
                    this.upButton.setY(top);
                }
                this.upButton.render(guiGraphics, mouseX, mouseY, partialTick);

                // 4) Down button [v]
                if (this.downButton == null) {
                    this.downButton = Button.builder(Component.literal("v"), btn -> moveDown())
                            .bounds(offsetX + 55, top, 20, 20).build();
                } else {
                    this.downButton.setX(offsetX + 55);
                    this.downButton.setY(top);
                }
                this.downButton.render(guiGraphics, mouseX, mouseY, partialTick);

                // 5) The text field
                if (this.textField == null) {
                    int textFieldX = offsetX + 80;
                    int textFieldY = top + (ENTRY_HEIGHT - 16) / 2;
                    int textFieldWidth = entryWidth - numberWidth - 85;
                    this.textField = new EditBox(CommandListScreen.this.font, textFieldX, textFieldY,
                            textFieldWidth, 16, Component.literal("entry"));
                    this.textField.setMaxLength(10000);
                    this.textField.setValue(this.initialText);
                } else {
                    this.textField.setX(offsetX + 80);
                    this.textField.setY(top + (ENTRY_HEIGHT - 16) / 2);
                    this.textField.setWidth(entryWidth - numberWidth - 85);
                }
                this.textField.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            private void moveUp() {
                int currentIndex = MyListWidget.this.children().indexOf(this);
                if (currentIndex > 0) {
                    List<MyListWidgetEntry> entries = MyListWidget.this.children();
                    MyListWidgetEntry above = entries.get(currentIndex - 1);
                    entries.set(currentIndex - 1, this);
                    entries.set(currentIndex, above);
                }
            }

            private void moveDown() {
                int currentIndex = MyListWidget.this.children().indexOf(this);
                if (currentIndex < MyListWidget.this.children().size() - 1) {
                    List<MyListWidgetEntry> entries = MyListWidget.this.children();
                    MyListWidgetEntry below = entries.get(currentIndex + 1);
                    entries.set(currentIndex + 1, this);
                    entries.set(currentIndex, below);
                }
            }

            @Override
            public @NotNull Component getNarration() {
                // Return the text that is displayed in the EditBox (or some placeholder).
                if (this.textField != null) {
                    return Component.literal(this.textField.getValue());
                } else {
                    return Component.literal("Entry without text");
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Let the text field handle clicks
                if (this.textField != null && this.textField.mouseClicked(mouseX, mouseY, button)) {
                    CommandListScreen.this.setFocused(this.textField);
                    return true;
                }
                // Buttons
                if (this.removeButton != null && this.removeButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (this.upButton != null && this.upButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (this.downButton != null && this.downButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                // If the text field is present, give it focus for editing
                if (this.textField != null) {
                    this.textField.setEditable(true);
                    this.textField.setFocused(true);

                    if (this.textField.keyPressed(keyCode, scanCode, modifiers)) {
                        SLOGGER.info("Detected key pressed in textField");
                        return true;
                    }
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (this.textField != null) {
                    this.textField.setEditable(true);
                    this.textField.setFocused(true);

                    if (this.textField.charTyped(codePoint, modifiers)) {
                        SLOGGER.info("Detected char typed in textField");
                        return true;
                    }
                }
                return super.charTyped(codePoint, modifiers);
            }

            public String getText() {
                return (this.textField == null)
                        ? this.initialText
                        : this.textField.getValue();
            }
        }
    }
}
