# File Decryption Over The Air Gap
The encrypted file has a similar structure as an encrypted password, i.e. every file is encrypted by a unique AES key, which is then encrypted by your RSA master key and written into the file header (among other parameters). 

The QR code sequence can only transport a very limited amount of data, the same holds true for the automated typing feature of *OneMoreSecret*. Therefore, the decryption is done in the following way: 

- Read the file header, retrieve the encrypted AES key, that belongs to the file.
- Generate a single use RSA key pair to protect the data while being transported between your PC and the smartphone (aka *Transport Key Pair*).
- Send the *public* Transport Key along with the (encrypted) AES key to *OneMoreSecret* using the QR code sequence. 
- Decryption process in *OneMoreSecret* is the same, the unprotected AES key of the file is now available on the smartphone.
- Encrypt AES key with the provided *public* RSA Transport Key generating *Key Response*.
- Type the (Base64 encoded) Key Response back to *omsCompanion* 
- Apply the *private* Transport Key obtaining the AES key of the file.
- Decrypt the file

...and Bob's your uncle!