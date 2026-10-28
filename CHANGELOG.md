# Changelog

## 0.2.1

- Fix empty LFM3 replies when hidden reasoning consumed the entire response limit. Bound reasoning separately and preserve room for the visible answer.
- Give code requests and follow-up code edits a larger answer budget without requiring Canvas. Encourage complete, compact, self-contained web files.
- Track response completion in saved chats. Exclude failed and interrupted turns from model context and add a retry action that replaces the last attempt.
- Mark truncated raw HTML/SVG files as partial instead of presenting them as complete.
- Show browser script errors inside Canvas instead of silently leaving a broken preview. Exercise real generated HTML under touch input in device regressions.
- Add **Fix with Ember** to send the current file and browser error back for correction. Show the attached source as a file card, preserve it in chat history, and retain it on retry.
- Match Material menus, sheets, dialogs, error surfaces and accent roles to Ember's light/dark palette. Add a close action to panels, improve file-editor insets, and enlarge response action targets.
- Keep an unsaved memory draft when saving fails, and respect the three-pinned-memory limit in the editor.
- Reject mismatched app IDs in the installer before changing the connected device.
- Keep the screen awake during foreground processing so a long reply is not interrupted by automatic screen sleep; clear the flag when processing ends.
- Add host regression checks for code budgets/context filtering and phone checks covering default-settings HTML generation, themes, file editors and retry.

## 0.2.0

- Add Liquid LFM3-1.7B with a separate pinned native runtime while retaining Bonsai's PrismML runtime.
- Add named downloadable code files and optional inline Canvas previews for HTML/CSS/SVG with browser JavaScript.
- Update app branding to Ember and preserve existing model files and conversations when updating the personal development package.
