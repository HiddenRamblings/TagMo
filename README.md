# TagMo's development is currently stagnant. If you want to contribute, please make sure to adress your PRs to the 'master2' branch, in order to keep it seperate from the original master. TagMo itself still works in it's current state. Thank you.


# What is TagMo?

TagMo is an Android app which allows for cloning Amiibos using blank NTAG215 NFC tags. It was created as a result of the "[DIY Amiibo cards](https://gbatemp.net/threads/diy-amiibo-cards.406978/)" thread and all the collaboration that took place in it.

# Features:

* Write dump files from legitimate Amiibos into an NTAG215 tag in a way that WiiU/3DS/switch devices will consider to be a legitimate Amiibo with the same functionality.
* Save data of a tag to a file and restore it to the same or different Amiibo/Clone provided they are of the same character/game/series.
* Beta feature: Edit some parameters of a SSB type amiibo allowing you to change special effects, stats etc.

# Limitations:

* Only NTAG215 tags can be used to clone Amiibos. no other type (eg: NTAG216/NTAG213) are supported.
* You will require the key files used in the Amiibo encryption. Please don't PM me about them as I am unable to share them. The thread linked above may help you find them.
* Once you write an NFC tag, it is effectively permanent.  If we rewrite the NFC tag, it will no longer be recognized as an Amiibo.
* **This means that this method can not replace Amiiqo or other Amiibo emulator devices**
* Only android phones are supported.

# Requirements:
* Amiibo Key Files. (See limitations/Don't ask me for these)
* Some blank NTAG 215 tags (Ebay/Aliexpress)
* Android phone with NFC (Tested on Nexus 5 running Android Lollipop)
* Amiibo dumps or real Amiibos which you can copy. (Don't ask me for these)

# Instructions:

## Cloning Amiibo 
* Install the app on the phone as usual.
* Make sure NFC is enabled.
* If this is the first time you run the app. Use the Menu->Load key(s) file to load the key file(s)
* Use "load tag" to load an amiibo dump file or "scan tag" to scan an Amiibo.
* Use "Write Tag (Auto)" and place the phone over a blank NTAG215 tag to write the data and create a Amiibo out of it.

## Saving Amiibo data to file 
* Click "Scan Tag" to scan the amiibo.
* Click "Save Tag" to save data to a file. (A file name will be generated based on the tag details and saved to the download folder.)

# Editing Super Smash Bros (SSB) data  
* Click "Scan Tag" to scan a amiibo. Make sure that the amiibo was once used in Super Smash Bros before.
* Click "Edit SSB Data" to edit properties related to SSB. Using this on non SSB amiibo will corrupt its saved data. requiring you to restore it from a save file (if you have one) or to reset it. It is recommended you backup the amiibo data using the steps above before trying this out.

# Credits
This app is a result of work by many others. Thanks go out to (In alphabetical order):

* \_Tim\_ - The originial idea
* 1RedOne - support/readme
* azsde - Bug fixes/maintenance
* Bombastisch - support and motivation
* brunomarchand - Change amiiboapi to https and upgrade to cmake
* FinalDoom - Bug fixes/maintenance
* H3llK33p3r - Images for browser and main screen.
* javiMaD - Encryption algo help
* Kidel - Bug Fixes
* N3evin - AmiiboAPI data
* NevetsTheSteve - Compatibility layer for Xperia phones
* North101 - File browser, Extended amiibo info, QR Code, general cleanup
* masterchan-777 - Bug fixes/maintenance
* possi - Wolf link editor
* socram8888 - Decryotion encryption code
* Supercool330 - Encryption algo help
* Others who preferred to remain anonymous.
* Many more who helped in testing.
