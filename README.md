# TagMo's default branch is stable. 

If you want to contribute, please make sure to adress your PRs to the 'experimental' branch of this repository in order to keep them seperate from the stable project. Thank you.

Current Stable
https://github.com/HiddenRamblings/TagMo/tree/master

Current Testing
https://github.com/HiddenRamblings/TagMo/tree/experimental

Archived Master
https://github.com/HiddenRamblings/TagMo/tree/master-archive

Archived Testing
https://github.com/HiddenRamblings/TagMo/tree/master2


# What is TagMo?

TagMo is an Android app which allows for cloning Amiibos using blank NTAG215 NFC tags. It was created as a result of the "[DIY Amiibo cards](https://gbatemp.net/threads/diy-amiibo-cards.406978/)" thread and all the collaboration that took place in it.

# Submitting Issues:

* The stable and testing branches contain the latest features, but some may be incomplete. If you see related progress, make sure to allow at least 24 hours before reporting. The exception is a fatal issue (TagMo becomes completely unusable).
* If you have a suggestion, feel free to submit it with the appropriate labels (ie. enhancement). Much like reporting a new issue, make sure to allow enough time before suggesting changes. The exception is requested feedback.
* Duplicate issues will be closed without warning and future comments on the duplicate will be disregarded. A link to the issue is duplicates will be provided upon closure.

# Features:

* Write dump files from legitimate Amiibos into an NTAG215 tag in a way that WiiU/3DS/switch devices will consider to be a legitimate Amiibo with the same functionality.
* Save data of a tag to a file and restore it to the same or different Amiibo/Clone provided they are of the same character/game/series.
* Beta feature: Edit some parameters of SSB type Amiibos, allowing you to change special effects, stats etc.

# Limitations:

* Only NTAG215 tags can be used to clone Amiibos. no other type (eg: NTAG216/NTAG213) are supported.
* You will require the key files used in the Amiibo encryption. Please don't PM me about them as I am unable to share them. The thread linked above may help you find them.
* Once you write an NFC tag, it is effectively permanent.  If we rewrite the NFC tag, it will no longer be recognized as an Amiibo.
* **This means that this method can not replace N2 Elite or other Amiibo emulator devices**
* Only android phones are supported.

# Requirements:
* Amiibo Key Files. (See limitations/Don't ask me for these)
* Some blank NTAG 215 tags (Ebay/Aliexpress)
* Android phone with NFC (Tested on Nexus 5 running Android Lollipop)
* Amiibo dumps or real Amiibos which you can copy. (Don't ask me for these)

# Instructions:

## Downloading TagMo
* Releases are tagged with the master and experimental branches, respectively.
* Each build is labeled with the hashtag for the last commit included in that build.
* Releases published from the experimental branch are also tagged as "Pre-Release" and will not appear on the main repository page. You must click on the "Releases" label to download these builds.
* Both tags include a signed copy of the apk in their assets. Builds from either tag can be installed over each other without uninstalling. Please include the branch or hash in any issue reports.

## Cloning Amiibo 
* Install the app on the phone as usual.
* Make sure NFC is enabled.
* If this is the first time you run the app. Use the Menu->Load key(s) file to load the key file(s)
* Use "load tag" to load an amiibo dump file or "scan tag" to scan an Amiibo.
* Use "Write Tag (Auto)" and place the phone over a blank NTAG215 tag to write the data and create a Amiibo out of it.

## Saving Amiibo data to file 
* Click "Scan Tag" to scan the amiibo.
* Click "Save Tag" to save data to a file. (A file name will be generated based on the tag details and saved to the download folder.)

## Writing to Power Tag
* Please see the [Power Tag (PowerSaves) Wiki](<https://github.com/HiddenRamblings/TagMo/wiki/Power-Tag-(PowerSaves)>) for setup and usage instructions

## Editing Super Smash Bros (SSB) data  
* Click "Scan Tag" to scan a amiibo. Make sure that the amiibo was once used in Super Smash Bros before.
* Click "Edit SSB Data" to edit properties related to SSB. Using this on non SSB amiibo will corrupt its saved data. requiring you to restore it from a save file (if you have one) or to reset it. It is recommended you backup the amiibo data using the steps above before trying this out.

# Credits
This app is a result of work by many others. Thanks go out to (In alphabetical order):

* \_Tim\_ - The originial idea
* 1RedOne - Support / README
* AbandonedCart - Rebuild, N2 Elite / legacy support
* azsde - Bug fixes / maintenance
* Bombastisch - Support and motivation
* brunomarchand - Change AmiiboAPI to https and upgrade to cmake
* FinalDoom - Bug fixes / maintenance
* H3llK33p3r - Images for browser and main screen
* javiMaD - Encryption algorithm help
* Kidel - Bug Fixes
* N3evin - AmiiboAPI data
* NevetsTheSteve - Compatibility layer for Xperia phones
* North101 - File browser, extended Amiibo info, QR code, general cleanup
* masterchan-777 - Bug fixes / maintenance
* possi - Wolf link editor
* socram8888 - Decryption / encryption code
* Supercool330 - Encryption algorithm help
* Others who preferred to remain anonymous
* Many more who helped in testing
