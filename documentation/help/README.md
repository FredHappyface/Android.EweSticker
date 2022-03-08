
<!-- omit in toc -->
# Help Guide

Support is provided via GitHub issues, please note this is provided on a voluntary basis

Please take a look at [Error Codes](#error-codes) first. This may provide some useful information
for why you are getting a certain error code. If an issue is created that is answered by this section
you'll likely be asked if you've read this :)

- [Error Codes](#error-codes)
	- [E031](#e031)
	- [E032](#e032)
	- [E033](#e033)
	- [E040](#e040)
	- [E041](#e041)
	- [E050](#e050)
- [Reach out](#reach-out)

## Error Codes

### E031
Some stickers failed to import (some number imported). Max stickers reached

This means that the total number of stickers that you are trying to import exceeds the
maximum number of stickers supported by EweSticker. Try and import fewer stickers,
see [Tutorial](/documentation/tutorials)

**NOTE:** that the maximum pack size is currently **128** and the total maximum number of stickers supported
is **4096**

If you feel strongly that the maximum limit should be increased, contribute to the discussion at
https://github.com/FredHappyface/Android.EweSticker/discussions/41 and make a request - Be sure
to explain why this would be useful. Simply creating an issue saying 'I want 20000 stickers!'
will likely result in the issue being closed

### E032
Some stickers failed to import (some number imported). Max pack size reached

This means that one of your sticker packs contains a number of stickers that exceeds the
maximum pack size supported by EweSticker. Try splitting the pack up into smaller chunks,
see [Tutorial](/documentation/tutorials)

**NOTE:** that the maximum pack size is currently **128** and the total maximum number of stickers supported
is **4096**

If you feel strongly that the maximum limit should be increased, contribute to the discussion at
https://github.com/FredHappyface/Android.EweSticker/discussions/41 and make a request - Be sure
to explain why this would be useful. Simply creating an issue saying 'I want 20000 stickers!'
will likely result in the issue being closed

### E033
Some stickers failed to import (some number imported). Unsupported formats found

This could be for a few reasons, perhaps you have a non sticker file in the sticker directory such
as a document in the wrong place. Alternatively this may result in a seemingly valid sticker not being
imported. Chances are that the sticker is not in a [supported format](/README.md#features).

### E040
(image type) not supported here

The application you are using doesn't support a sticker format or the compat-format

Unfortunately, nothing can be done by EweSticker to solve this, you may need to contact the application
developer you are trying to send a sticker to

### E041
Unexpected IOException when converting sticker

This is an unexpected error and happens when creating a compat-sticker to send to the application.
Please open an issue and provide as much information as you can. E.g. Android Version, phone
manufacturer, app you are trying to send the sticker in

### E050
IllegalStateException when switching packs. Try switching away from and back to EweSticker

This sometimes happens if you leave EweSticker as the current keyboard and switch back to it. The best
way to solve this is to tap the back button in the pack selector and switch back to EweSticker.
Please open an issue and provide as much information as you can. E.g. Android Version, phone
manufacturer, app you are trying to send the sticker in.

## Reach out

Support is provided via GitHub issues, please note this is provided on a voluntary basis

You are therefore not entitled to free customer service (that is not to say that contributions/ issues and questions are not welcome - more reminding you that project maintainers are well within their rights to prioritize other issues).

https://github.com/FredHappyface/.github/blob/master/SUPPORT.md provides a little more info
from the types of support you can expect

Please make sure to read https://github.com/FredHappyface/Android.EweSticker/issues/21 before
opening an issue, this may seem a bit grumpy but chances are I won't be able to help with your
issue if you do not fill in the template provided

To open a new issue click the following link: https://github.com/FredHappyface/Android.EweSticker/issues/new/choose

**NOTE:** you will need to have a GitHub account to open issues (create one at https://github.com/signup)
