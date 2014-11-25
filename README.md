SRW GC SP Extractor by Dashman
--------------------

This is a command line application in java.

To execute it, open a console, navigate to the folder where the application is and execute the following:

java -jar sp_extract.jar <pak_file>

* You obviously need to have Java installed
* This application will work for bmes.pak and bpilot.pak, I haven't found any other PAK file with SP files inside.

UPDATE!!

Use SRW GC Bin Splitter to extract the contents of add00dat.bin.
Place sp_extract.jar inside the folder with said contents.
Open a console / shell and navigate to said folder.

Execute: java -jar sp_exract.jar -f

This will recognize and extract all images inside the SPR files contained in the folder.
Some extra files will be extracted as well (they're meant to be used during reinsertion).


UPDATE 2!!

Now the SP Extractor can join the extracted files back into an SP / SPR file.

Execute: java -jar sp_exract.jar -j <sp_file> <folder>

For example: java -jar sp_exract.jar -j test.spr 3509.SPR_extract

where "folder" is the folder containing the extracted files and "sp_file" is the name of the file you'll be creating.

Keep in mind the following:

* The "sp_file" will be created INSIDE "folder". This is to avoid overwriting SP / SPR files outside the folder accidentally.
* The "folder" has to contain ALL the originally extracted files. BMP files can be edited, of course.
* The edited BMP files *MUST* be indexed with 256 colours. Their original dimensions *MUST* be the same.