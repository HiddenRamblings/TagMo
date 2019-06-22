amiitool
========
amiibo™ encryption/decryption tool

Usage
=====
**amiitool expects a binary dump. It will not work with XMLs or hexadecimal text files**. Aside from this, amiitool is very easy to use and has a very simple syntax.

First, you have to specify an operation, either ```-e``` (encrypt and sign) or ```-d``` (decrypt and check).

You need also to specify a file using ```-k [keys]``` switch, indicating which file contains the cryptographic master keys.

Optionally, you may also specify input and output files using ```-i [input]``` and ```-o [output]```. If input or output are unspecified, amiitool will default to stdin and stdout, respectively. This lets you pipe amiitool inputs and outputs with standard shell tools such as xxd.

When decrypting, by default amiitool will be in strict mode, and will abort and raise an error if the cryptographic signature embedded in the encrypted dump is not valid. If you want to disable checking, use ```-l``` switch to put amiitool in lenient mode.

Examples
--------

- Decryption "mario.bin" and displaying hex to terminal:
   > amiitool -d -k retail.bin -i "mario.bin" | xxd

- Encryption "modified.bin" to "signed.bin":
   > amiitool -e -k retail.bin -i "modified.bin" -o "signed.bin"
