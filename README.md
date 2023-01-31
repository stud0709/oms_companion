# omsCompanion
The supplementary desktop software for OneMoreSecret.

### Disclaimer
This is a very early version of the software. Use it at your own risk.

## Setting Up
Download and unzip the release into a folder. Use `oms.cmd` to start the application. It will appear in your system tray.

### Creating Your Private Key
Open the context menu from the system tray icon and click on *Cryptography... -> New Private Key*. The wizard will guide you through the process.

![New Private Key](/readme_images/new_private_key.png)

*Store Public Key for later use* checkbox - this will copy your public key to the subfolder */public* within the app folder, so that you can encrypt data with this key on your PC.

**Store the generated HTML file in a secure location or print it out**. Also do not forget to remember the transport password - you will need it to import your private key into the phone, so the next time you will be using it may be in a couple of years from now. 

### Importing the private key.

The key generation wizard will show you the sequence of QR codes to be scanned with OneMoreSecret app immediately after you click on *Create*. 

If you are restoring your private key from the backup document, you will have to scan all codes one by one. The *Remaining Codes* list will display the codes still missing. 

![Import Key](/readme_images/importing_pk.jpg)

After you have scanned all the codes, the app will ask you to enter your transport password. You can also change the key alias prior to clicking *Decrypt* and then *Save*

![Import Key 2](/readme_images/importing_pk_2.jpg)

## Encrypting Data
If you double click on the tray icon, omsCompanion will check the clipboard for text. The text will be then encrypted with your public key and copied back to the clipboard. A sample result is

oms://MQlSU0EvRUNCL1BLQ1MxUGFkZGluZwlta1g4cHdYanNtT2FSSkl2dHRuZUJUTEVBUHA3dHY4QVorMkZmT0J5dVZzPQlBRVMvQ0JDL1BLQ1M1UGFkZGluZwlRYlB6L2hBTVlDVzFJL1VQMTdlWGZRPT0JcTltaDI3aHIvVWtNUE84NnNsa0ZpZjdCRy9Bb1FsYTBUYWtkbXdRcWtKY2ZEL1VjOFBFaUN0Y041Uy8yeFhsdm9jYkV0SUtyRmkwMzQrd2FrbUJqejRETXdkV01VQ2pGSnpVVDNUUFN6WnZ1Z2lIdVkxYlczYVAxNU5LWE50eGNIZkl3cUZHOWF5M0VSREhIbm4zSEVLWjYyN3RsRHpJZHlBcHA5WlduQXNXQTF4QTdaZUNUTzBBTG5XSmtMVTNPTHNVNkIrSk1kZzNWaVM1ckZZQ0E4TkFHLzZWcytYRHpTK1gzZFIvY3dWOEFXRE9Mc3k5TVE4bXN5eWljUExvT1V2aGI4b2pSTDh4TVV3ZmZXb2loWWg0SWFnRzF4YmRES0M4WlJNViswUmNzaUZZaTZOaitLSGphajM4YllDM3RDWEdlZi9IK01CSGhtZ0lhK0VoZ1ZBPT0JQXZqakE3TFRzQzVPbjFtK0hqSi9qdHVJbWVjemVON0tHN0V4TW5CeEQyMXFua2sxY1ZndnVyMGRyeEpLOEw4ZkRKK3FoeDR2M0t4MXN3ZHBzY0N2Tk53V0dIS3pQWVJtR3VlWmEwZTVWeVU9

In addition to the text format, you can also generate an animated `.gif` file or a BASE64 encoded `GIF` data, which is useful if you want to embed the image into the `<img src="data:image/gif;base64,...]/>` tag (replacing `...` with the BASE64 encoded data)

![Encrypting Data](/readme_images/encrypting.png)

## Decrypting Data
omsCompanion is continuously monitoring your clipboard. If it finds a text starting with `oms://`, it will generate a QR code sequence out of it, empty the clipboard and show the code sequence. You can also scan the animated `.gif` files created earlier from the encryption dialog.

For more information on decripting see OneMoreSecret documentation. 