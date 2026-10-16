# Compact single-choice conventions

These settings-specific conventions extend the project-wide [UI design-system guidelines](ui-design-system-guidelines.md). Follow the shared Miuix, material, layout, reuse, and user-exception rules here as well.

Use these conventions for settings and other single-choice controls throughout Yumerin:

- A single-choice item is a compact, Nexio-style anchored popup. Its row shows the current value on the right with an up/down chevron. Selecting an option updates the displayed value and closes the popup. Standalone settings save immediately; edit forms keep the selection in their draft until the form is saved.
- Standalone switches and choices apply immediately. Sliders apply when the drag ends, time pickers apply when the user confirms that picker, and numeric fields apply each valid value as it is entered. Do not add a page-level Save button to standalone settings pages; invalid or incomplete numeric input must not replace the last valid setting.
- A setting that opens a subpage shows a right-facing arrow. Keep this distinct from an inline choice row.
- Render single-choice controls with the compact popup instead of radio-button groups, segmented buttons, or large selection cards.
- Boolean settings remain switches, multi-select controls remain multi-select and use checkboxes, and time, number, and free-text settings remain input controls.

Follow Nexio's settings-component scope: Miuix `Scaffold` and `Card` for preference-page structure, `ArrowPreference` for subpage navigation, `SwitchPreference` for booleans, and `OverlayDropdownMenu` for single choice. Use Miuix checkboxes for multi-select lists.

`OverlayDropdownMenu` uses Miuix `OverlayListPopup`; pages that host it must be inside a Miuix `Scaffold`. Keep that scaffold around the page content, including when the selector appears in a dialog.
