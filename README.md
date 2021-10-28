# TagMo's default branch is stable. 

If you want to contribute, please make sure to adress your PRs to the 'experimental' branch. Thank you.

* [Current Stable - "master"](https://github.com/HiddenRamblings/TagMo/tree/master)
    * [Archived Stable - "master-archive"](https://github.com/HiddenRamblings/TagMo/tree/master-archive)

* [Current Testing - "experimental"](https://github.com/HiddenRamblings/TagMo/tree/experimental)
    * [Archived Testing - "master2"](https://github.com/HiddenRamblings/TagMo/tree/master2)

* https://www.reddit.com/r/tagmo/


# What is TagMo?

TagMo is an Android app for cloning Amiibo to blank NTAG215 NFC tags, Power Tags, and Amiiqo / N2 Elite devices.

It was created as a result of the "[DIY Amiibo cards](https://gbatemp.net/threads/diy-amiibo-cards.406978/)" thread and all the collaboration that took place in it.

# Submitting Issues:

* master and experimental contain the latest features, but some may be incomplete. 
    * Please make sure to allow 24 hours before reporting changes.
    * The exception is a fatal issue (TagMo becomes unusable).
* If you have a suggestion, submit it with the appropriate labels (ie. enhancement). 
    * Much like reporting issues, make sure to allow additional time. 
    * Requested feedback on changes will be appropriately tagged.
* Duplicate issues will be closed without warning and future comments on the duplicate will be disregarded. 

# Features:

* Write dumped legitimate Amiibo to an NTAG215 tag in a way that WiiU / 3DS / Switch devices will recognize as Amiibo.
* Save data from a tag to a file and restore it to the same or different Amiibo / clone of the same character / game / series.
* Beta feature: Edit some parameters of SSB Amiibo (change special effects, stats, etc.) and other specialized data.

# Limitations:

* Only NTAG215 tags can be used to clone Amiibo. No other type (eg: NTAG216/NTAG213) are supported.
* You will require the key files used in Amiibo encryption. Keys cannot be shared for legal reasons.
* Once you write an NFC card, it is effectively permanent.  Rewriting will break Amiibo recognition.
* **This means that writing to cards can not replace N2 Elite or other Amiibo emulator devices**
* Only Android phones with NFC capabilities are supported.

# Requirements:
* Amiibo Key Files. (These cannot be shared for legal reasons. See limitations)
* Blank NTAG 215 tags, Power Tags, or Amiiqo / N2 Elite (Ebay / Aliexpress / Amazon)
* Android phone with NFC (Tested on various Samsung, Asus, and Nexus devices)
* Previous Amiibo backups or Amiibo. (Again, these cannot be shared for legal reasons)

# Instructions:

## Downloading TagMo
* Releases are tagged with the master and experimental branches, respectively.
* Each build is labeled with the hashtag for the last commit included in that build.
* Releases published from the experimental branch are also tagged as "Pre-Release" and will not appear on the main repository page. You must click on the "Releases" label to download these builds.
* Both tags include a signed copy of the apk in their assets. Builds from either tag can be installed over each other without uninstalling. Please include the branch or hash in any issue reports.

## Cloning Amiibo 
* Install TagMo on the phone and enable NFC, if necessary
* If this is the first time you run the app. Use the Settings -> Import Keys option to load the key file(s)
* Allow TagMo to scan for Amiibo files and click one or press the pink tag icon in the bottom right to scan an Amiibo
* Click "Write" and place the phone over a blank NTAG215 tag to write the data and create an Amiibo from it

## Saving Amiibo data to file 
* Click the pink NFC icon in the bottom right of the main browser
* Scan the Amiibo and wait for the preview window to appear
* Click "Save" and follow the prompts to input a name and save
* Bin files are saved to "TagMo Backups" in the selected root folder

## Writing to Power Tag
* Please see the [Power Tag (PowerSaves) Wiki](<https://github.com/HiddenRamblings/TagMo/wiki/Power-Tag-(PowerSaves)>) for setup and usage instructions

## Editing Super Smash Bros (SSB) / Wolf Link / Custom data  
* Click the pink NFC icon to scan or select an Amiibo from the browser.
    * Make sure SSB Amiibo were once used in Super Smash Bros.
* Click the three dot button and select the "Edit Properties" option
* Enable the editor using the slider on the right.
    * Enable "APP DATA" for SSB and Wolf Link Amiibo and select App ID
* When finished, click the disk icon and write the edited Amiibo

# Credits
This app is a result of work by many others. Thanks go out to (In alphabetical order):

* \_Tim\_ - The originial idea
* 1RedOne - Support / README
* AbandonedCart - Rebuild, N2 Elite, and more
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
