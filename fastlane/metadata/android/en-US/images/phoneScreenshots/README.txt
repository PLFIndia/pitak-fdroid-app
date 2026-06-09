Drop your phone screenshots in THIS folder.

Requirements (F-Droid / fastlane convention):
- PNG or JPG, portrait, taken on the phone (the gray-spine F-Droid build).
- Name them in display order: 1.png, 2.png, 3.png, ...
- 4 to 6 screenshots is the sweet spot (minimum 1, max useful ~8).

Suggested set (one per core capability):
  1.png  Library screen with several books (cover grid or cards)
  2.png  Book detail screen
  3.png  ISBN scanner / add-a-book flow
  4.png  Loans & borrowers (vault unlocked)
  5.png  Wishlist or the published-library web view

Capture on the phone:
  adb -s <device> exec-out screencap -p > 1.png
(or use the phone's own screenshot button and copy the files here.)

Delete this README once real screenshots are in place — F-Droid ignores
non-image files here, but keeping it tidy is nicer.