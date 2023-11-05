# ![App Icon](/readme_images/qr-code.png)  omsCompanion
The supplementary desktop software for [OneMoreSecret](https://github.com/stud0709/OneMoreSecret). Its main purpose is to bridge the *Air Gap* between your desktop PC and the OneMoreSecret app on your phone.

It also mirrors some features of the smartphone app, which may better fit your daily routine.

## Disclaimer
This is a very early version of the software. Use it at your own risk.

## Setting Up
This software requires Java Runtime Environment 17 or later. Download the release file into a subfolder of your user folder. 
- If your java runtime has registered with the .jar file type, you just doubleclick `omscompanion.jar`.
- If you have your Java runtime on your `path`, use `oms.cmd` to start the application (it contains just on line: `java -jar omscompanion.jar`)
- If you have a Java installation not on your `path`, modify `omscompanion.cmd` accordingly (`path_to_your_java_folder\bin\java -jar omscompanion.jar`).

omsCompanion will appear in your system tray.

If you rely on `omscompanion.jar` and don't want Windows prompt to show up, you can modify the command as follows: `start javaw -jar omscompanion.jar`


## Creating a Private Key

This is a desktop implementation of the OneMoreSecret [New Private Key](https://github.com/stud0709/OneMoreSecret/blob/master/new_private_key.md) feature. You can either generate a private key on your smartphone directly or create it in omsCompanion and import it into your smartphone. 

Open the context menu from the system tray icon and click on *Cryptography... -> New Private Key*. 

![New Private Key](/readme_images/new_private_key.png)

*Store Public Key for later use* checkbox - this will copy your public key to the subfolder */public* within the app folder, so that you can encrypt data with this key on your PC.

**Store the generated HTML file in a secure location or print it out**. Also do not forget to remember the transport password - you will need it to import your private key into the phone, so the next time you will be using it may be in a couple of years from now. 

### Importing the Public Key into the Phone.

The key generation wizard will show you the sequence of QR codes to be [scanned](https://github.com/stud0709/OneMoreSecret/blob/master/qr_scanner.md) with OneMoreSecret app immediately after you click *Create*. After the successful scan, the [import screen](https://github.com/stud0709/OneMoreSecret/blob/master/key_import.md) will open on your smartphone.

## Encrypting Passwords and TOTP Tokens
If you double click the tray icon, *omsCompanion* will check the clipboard for text. If the text starts with `oms00_`, a QR code sequence will be generated (see the [OneMoreSecret Tutotial](https://github.com/stud0709/OneMoreSecret/blob/master/hello_world.md) for a sample). Any other text found in the clipboard will be encrypted with the public key of your choice and copied back to the clipboard. *Default* will make the selected public key your default one. 

In addition to the text format, you can also generate an animated `.gif` file or a BASE64 encoded `GIF` data, which is useful if you want to embed the image into the `<img src="data:image/gif;base64,...]/>` tag (replacing `...` with the BASE64 encoded data). This should also work for markdown documents.

![Encrypting Data](/readme_images/encrypting.png)

## Decrypting Password and TOTP Tokens
By default, *omsCompanion* is continuously monitoring your clipboard (you can disable this feature by clicking *Monitor clipboard* in the context menu of the tray icon). If it finds a text starting with `oms00_`, it will generate an QR code sequence out of it, empty the clipboard and show a pop-up window:

![QR pop-up](readme_images/QR_pop_up.png)

The pop-up also offers a context menu for `.gif` file creation and text output of the data.

You can also scan the `.gif` files created earlier by the encryption dialog.

For more information on decripting see OneMoreSecret [documentation](https://github.com/stud0709/OneMoreSecret/blob/master/decrypted_message.md). 

## Encrypting Files
Copy files you want to encrypt to the clipboard and double-click the system tray icon of *omsCompanion*. The files will be encrypted with the public key of your choice. The resulting file(s) of type `.oms00` will be either set to the clipboard (if you encrypt a single file) or written to the folder of your choice.

You may also like to use the [file encryption](https://github.com/stud0709/OneMoreSecret/blob/master/encrypt_file.md) functionality of *OneMoreSecret* to encrypt files on your smartphone. 

## Decrypting Files
In a similar way, *omsCompanion* will decrypt files (in the current version, only one file at a time). 

To start encryption process, copy the encrypted file to the clipboard and double-click the system tray icon of *omsCompanion*.

[RTM](file_decryption_air_gap.md) for technical details on file decryption.

## Password Generator
![Password Generator](/readme_images/password_generator.png)

Here you can generate a password according to the guidelines:
- A...Z - upper-case letters
- a...z - lower-case letters
- 0...9 - digits
- !"&#... - special characters
- Similar - characters, that can be easily confused (like `capital I` and `one` or `zero` and `capital O`)

You can also edit the password suggestion manually. Once you are finished, the password can be encrypted with the tools you already know from the [encryption dialog](#encrypting-data).

## Importing Public Key

![public_key_import](/readme_images/public_key_import.png)

Public keys are needed to encrypt data. Only the person who owns the corresponding private key will be able to decrypt it. 

If you generate your public key [in omsCompanion](#creating-a-private-key) and *store public key for later use*, you are fine. If you [restore](https://github.com/stud0709/OneMoreSecret/blob/master/key_import.md) the key from the backup document onto your smartphone, you will need to import the public key into omsCompanion. You can do it from the [Key Management](https://github.com/stud0709/OneMoreSecret/blob/master/key_management.md) screen. The BASE64 encoded data from your smartphone must be copied or [auto-typed](https://github.com/stud0709/OneMoreSecret/blob/master/autotype.md) into the *Key Data (BASE64)* field. Enter an *alias* for the key and *save*.

Now you can use this key to encrypt the data.

The public key storage is accessible from the *Cryptography -> Public Key Folder* context menu of the tray icon.

## Roadmap and Bugs
For feature requests and bug report, please open a [GitHub Issue](https://github.com/stud0709/oms_companion/issues). 
