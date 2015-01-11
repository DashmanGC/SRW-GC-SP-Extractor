/*
 * Copyright (C) 2013 Dashman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package srwgcspextractor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonatan
 */
public class Main {

    public static class IndexEntry{
        //public String name;
        public int offset;
        public int size;

        public IndexEntry(){
            //name = "";
            offset = 0;
            size = 0;
        }

        public IndexEntry(int o, int s){
            //name = n;
            offset = o;
            size = s;
        }
    }

    static String pak_file;
    static RandomAccessFile f;
    static int tex_counter = 0;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here

        // IMPORTANT: The files that seems to contain SP files are bmes.pak and bpilot.pak
        // Don't use this on anything else.

        if (args.length == 3){
            if (args[0].equals("-j")){ // Join folder
                try{
                    repackSP(args[1], args[2]);
                    
                    //System.out.println("File " + args[1] + " processed sucessfully.");
                    return; // END
                }catch (IOException ex) {
                    System.err.println("ERROR: Couldn't read file.");   // END
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                System.out.println("ERROR: Wrong number of parameters: " + args.length);
                System.out.println("USE:\n java -jar sp_extract <pak_file>");
                System.out.println("OR:\n java -jar sp_extract -f");
                System.out.println("OR:\n java -jar sp_extract -j <sp_file> <extracted_folder>");
                return;
            }
        }
        else if (args.length != 1){
            System.out.println("ERROR: Wrong number of parameters: " + args.length);
            System.out.println("USE:\n java -jar sp_extract <pak_file>");
            System.out.println("OR:\n java -jar sp_extract -f");
            System.out.println("OR:\n java -jar sp_extract -j <sp_file> <extracted_folder>");
            return;
        }

        if (args[0].equals("-f")){  // Scan the current folder for SP and SPR files
            File folder = new File(".");
            System.out.println(folder.getAbsolutePath());
            File[] listOfFiles = folder.listFiles(new FilenameFilter(){
                public boolean accept(File dir, String filename) {
                    return (filename.endsWith(".SPR") || (
                            filename.endsWith(".sp")
                            )); }
            });

            try{
                for (int i = 0; i < listOfFiles.length; i++){
                    System.out.println(listOfFiles[i].getName());
                    pak_file = listOfFiles[i].getName();    // Not really a PAK file, but we reuse the variable
                    f = new RandomAccessFile(pak_file, "r");

                    if(pak_file.endsWith(".sp")){
                        // SP file - Start reading from the beginning
                        extractSP(0, false);
                    }
                    else{
                        // SPR file - Has a 32-byte header
                        extractSP(32, true);
                    }

                    f.close();
                }
            }catch (IOException ex) {
                System.err.println("ERROR: Couldn't read file.");   // END
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }

            System.out.println("Finished.");
        }
        else{   // Open a PAK file
            pak_file = args[0];

            // Try opening the file
            try{
                f = new RandomAccessFile(pak_file, "r");
                // Read the header / index and obtain the offsets
                readHeader();
            }catch (IOException ex) {
                System.err.println("ERROR: Couldn't read file.");   // END
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }


    public static void readHeader() throws IOException{
        // Read the first 4 bytes of the file
        byte[] header = new byte[4];
        f.read(header);

        // If the second byte is 00, we have a valid file
        if (header[1] == 0){
        // If it starts with 00 00 it has an index -> next 2 bytes indicate the size
            if (header[0] == 0){
                int index_size = header[3] & 0xff; // Take the lower part
                index_size += ((header[2] & 0xff) << 8);   // Add the upper part
                //System.out.println("Size: " + index_size);
                getIndexed(index_size);
            }
        // If it starts with a number (like 01 00), it's a series of files in sequence -> must read the size from their headers
        // Incidentally, no file has more than 255 entries, so it's safe to assume the second byte is always 00
            // NEVER USED
            /*else{
                int size = header[0] & 0xff; // Take the lower part
                size += ((header[1] & 0xff) << 8);   // Add the upper part
                getSequential(size);
            }*/
        }
        // Otherwise, indicate the file is not supported
        else{
            System.err.println("ERROR: Unsupported file."); // END
            f.close();
        }
    }


    // Takes a 4-byte hex little endian and returns its int value
    public static int byteSeqToInt(byte[] byteSequence){
        if (byteSequence.length != 4)
            return -1;

        int value = 0;
        value += byteSequence[3] & 0xff;
        value += (byteSequence[2] & 0xff) << 8;
        value += (byteSequence[1] & 0xff) << 16;
        value += (byteSequence[0] & 0xff) << 24;
        return value;
    }

    // Meant to be used in PAK files with no index
    // In the end it was never needed.
    //The Bin Splitter extracts those files to a folder and this program can read those files
    public static void getSequential(int size) throws IOException{
        System.out.println("Size: " + size);

        // Do stuff

        f.close();  // END
    }


    public static void getIndexed(int size) throws IOException{
        // Prepare an arraylist of IndexEntry
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        for (int i = 0; i < size; i++){
            // Every entry in the index has 32 bytes for the name + 4 bytes for the offset + 4 bytes with the length of the file
            byte[] entry_block = new byte[40];
            f.read(entry_block);

            // We only want the SP files -> Check if the names end with .sp
            if (entry_block[29] == '.' && entry_block[30] == 's' && entry_block[31] == 'p'){
                // Add the ones that do to the list
                IndexEntry ie = new IndexEntry();
                byte[] seq = new byte[4];
                seq[0] = entry_block[32];
                seq[1] = entry_block[33];
                seq[2] = entry_block[34];
                seq[3] = entry_block[35];
                ie.offset = byteSeqToInt(seq);

                seq[0] = entry_block[36];
                seq[1] = entry_block[37];
                seq[2] = entry_block[38];
                seq[3] = entry_block[39];
                ie.size = byteSeqToInt(seq);

                entries.add(ie);
            }
        }

        // Extract every file in the final list
        for (int i = 0; i < entries.size(); i++){
            //System.out.println(i + " - Offset: " + entries.get(i).offset + " Size: " + entries.get(i).size);
            if (extractSP(entries.get(i).offset, false) < 0)
                System.err.println("ERROR: Index doesn't point to SP file!");
        }

        // Inform of results
        System.out.println("Finished. Found " + tex_counter + " SP files.");

        f.close();  // END
    }


    // Extracts SP file's content from global file f
    public static int extractSP(int start, boolean spr) throws IOException{
        int offset = 0;

        if (spr){   // Save the SPR header too
            f.seek(0);
            byte[] head_spr = new byte[32];
            f.read(head_spr);
            writeOther(head_spr, "", ".hed");
        }

        f.seek((long) start);   // Set the file pointer to the beginning of the SP file

        // 1) Process the palette header (128 bytes)
        byte[] header = new byte[128];
        f.read(header);

        // Bytes 0 - 3 are just 'PAL_'
        if (header[0] != 'P')
            return -1;  // ERROR

        // Bytes 4 - 7 are the size of the palette data (Header + palette colours) in big endian ('80 04' would be '480' in hex)
        byte[] seq = new byte[4];
        // We read it in reverse because it's big endian
        seq[0] = header[7];
        seq[1] = header[6];
        seq[2] = header[5];
        seq[3] = header[4];
        int pal_size = byteSeqToInt(seq);
        //System.out.println("Palette size: " + pal_size);

        offset += pal_size;

        // Bytes 8 - 11 unknown
        // Bytes 12 - 15 unknown
        // Variable? file name (it seems to take 10 bytes normally) * IT'S ALWAYS 10 BYTES
        boolean stop = false;
        String filename = "";
        for (int i = 16; !stop && i < 26; i++){ // Get the name
            filename += (char) header[i];
            /*if (header[i] == 'p'){  // Stop for .sp files
                if(header[i-1] == 's' && header[i-2] == '.'){
                    stop = true;
                    //System.out.println("Found file: " + filename);
                }
            }
            else if (header[i] == 'i'){ // Stop for .bi files
                if(header[i-1] == 'b' && header[i-2] == '.'){
                    stop = true;
                }
            }*/
            //else
            //    System.out.println("Character found: " + (char) pal_h[i]);
        }
        System.out.println("Found file: " + filename);

        // 'PAL00' (5 bytes)
        // Next 2 bytes unknown
        // Next 2 bytes unknown
        // Next 4 bytes unknown
        // Next 4 bytes unknown
        // The rest is filled with zeroes

        // 2) Process the palette colours

        // Each colour has 4 bytes (B, G, R and Alpha)
        // An easy way to determine the number of colours: (size of palette data - 128) / 4
        // *First colour seems to be the "transparent" colour.
        int colour_size = pal_size - 128;
        byte[] colours = new byte[colour_size];
        f.read(colours);

        // Save the original palette as a file (before correcting the colours)
        byte[] full_palette = new byte[pal_size];

        for (int i = 0; i < header.length; i++)
            full_palette[i] = header[i];

        for (int i = 0; i < colours.length; i++)
            full_palette[header.length + i] = colours[i];

        writeOther(full_palette, "", ".pal");

        // We have to rearrange the colours if we want the bmp to show them properly
        byte aux;
        for (int i = 0; i < colours.length; i += 4){    // Swap the B and R (go from BGRA to RGBA)
            aux = colours[i];
            colours[i] = colours[i+2];
            colours[i+2] = aux;
        }

        // *The previous palette can be applied to several textures inside the SP file

        boolean moreTex = false;
        int counter = 0;
        // 3) Process the texture header (128 bytes)
        do{
            //System.out.println("Reading texture at " + (start + offset));
            counter++;
            f.read(header); // We reuse the same byte array (same size)

            // Same format as the palette header, but replace 'PAL' with 'TEX'
            seq[0] = header[7];
            seq[1] = header[6];
            seq[2] = header[5];
            seq[3] = header[4];
            int tex_size = byteSeqToInt(seq);
            offset += tex_size;

            // The 4 bytes right after TEXxx indicate the dimensions of the texture (for example '00 40 00 40' is 64x64)
            //int skip = 16 + filename.length() + 5;
            // WRONG!!!!
            // We have to take into account TEXxx and one more byte! (normally a 00), so that's 32 bytes
            //int skip = 31;
            int skip = 32;
            seq[0] = 0;
            seq[1] = 0;
            seq[2] = header[skip + 1];
            seq[3] = header[skip];
            int dimX = byteSeqToInt(seq);

            seq[0] = 0;
            seq[1] = 0;
            seq[2] = header[skip + 3];
            seq[3] = header[skip + 2];
            int dimY = byteSeqToInt(seq);

            // Next 2 bytes seem to indicate the color bit depth: 00 04 is 4-bit (16 colours) and 00 08 is 8-bit (256 colours)
            //byte col_depth = header[skip + 5];
            byte col_depth = header[skip + 4];

            // SAVE the header of the texture!!
            // There's some bytes I honestly have no idea what they're for, better to just save the whole header
            writeOther(header, "_" + counter, ".hed2");

            //System.out.println("Offset: " + start + " Tex. size: " + tex_size + " - Dim X: " + dimX + " - Dim Y: " + dimY + " - Col. depth: " + (int) col_depth);

            // 4) Process the texture data

            // Size of texture data = size found in header - 128
            int tex_pixels = tex_size - 128;
            byte[] pixels = new byte[tex_pixels];
            f.read(pixels);

            // Turns out that if the image is stored as 4bpp, the nibbles have to be reversed
            if (col_depth == 4){
                //System.out.println("4bpp\n");
                for (int i = 0; i < pixels.length; i++){
                    //System.out.println("Before: " + ( (pixels[i] & 0x0f) << 4) + " - " + ( (pixels[i] & 0xf0) >> 4));
                    pixels[i] = (byte) ( ( (pixels[i] & 0x0f) << 4) | ( (pixels[i] & 0xf0) >> 4) );
                    //System.out.println("After: " + pixels[i]);
                }

                // If that wasn't enough, in this mode the lines go like 1, 0, 3, 2, 5, 4, 7, 6 ... and so on
                // WRONG! This happens because we don't take into account that in 4bpp the width must be halved
                /*System.out.println("DimX: " + dimX + " - DimY: " + dimY);

                int dimX_4bpp = dimX / 2;

                for (int i = 0; i < dimY; i += 2){
                    for (int j = 0; j < dimX_4bpp; j++){
                        byte current_byte = pixels[(i * dimX_4bpp) + j];
                        byte byte_next_line = pixels[(i * dimX_4bpp) + j + dimX_4bpp];
                        pixels[(i * dimX_4bpp) + j + dimX_4bpp] = current_byte;
                        pixels[(i * dimX_4bpp) + j] = byte_next_line;
                    }
                }*/
            }

            // The texture data is stored upside-down. We can fix that.
            int width = dimX;
            if (col_depth == 4)
                width = width / 2;

            byte[] pixels_R = pixels.clone();
            for (int i = 0, j = pixels.length - width; i < pixels.length; i+=width, j-=width){
                for (int k = 0; k < width; ++k){
                    //System.out.println("Length: " + pixels.length + " i: " + i + " j: " + j + " k: " + k);
                    pixels[i + k] = pixels_R[j + k];
                }
            }

            // 5) Write BMP file with the palette and texture data
            //System.out.println("Proceed to save a BMP file.");
            writeBMP(filename, colours, pixels, dimX, dimY, col_depth, counter);

            // If the next 4 bytes are 'TEX_', go back to step 3)
            // If instead of another 'TEX_' we got 'CEL_', we're done with this SP file.
            // The 4 bytes after CEL_ indicate the size of that block in big endian (f8 01 00 00 is like '1f8' = 504)
            f.read(seq);
            if (seq[0] == 'T'){
                f.seek(f.getFilePointer() - 4); // Get back 4 bytes, so we can start again
                moreTex = true;
                //System.out.println("Offset: " + (start + offset));
            }
            else{   // Get the size of the CEL_ block
                //System.out.println("Offset: " + (start + offset) + " - CEL");
                moreTex = false;
                byte[] cel_size = new byte[4];
                f.read(cel_size);
                seq[0] = cel_size[3];
                seq[1] = cel_size[2];
                seq[2] = cel_size[1];
                seq[3] = cel_size[0];

                int c_size = byteSeqToInt(seq);

                // Save the CEL file
                f.seek(offset + start);

                byte[] cel = new byte[c_size];

                f.read(cel);

                writeOther(cel, "", ".cel");

                offset += c_size;
            }
        }while (moreTex);
        // The END block is always 16 bytes long, add the sizes of the two last blocks to the final offset and return it.
        offset += 16;

        // We need to return the size of the SP file to skip it if we're not dealing with indexed files
        return offset;
    }


    // Finds the extracted files from an SP / SPR file inside a folder
    // and builds an SP / SPR file with them in the same folder
    public static void repackSP(String filename, String folder) throws IOException{
        // We have the advantage of having extracted pretty much all the contents of
        // the SP / SPR previously in the folder. Most of the data is stored in files
        // And it's just a matter of grabbing them and writting the bytes into a new file.
        //
        // However, the BMP files must be processed:
        // Image data is stored upside-down inside BMP files, so we have to flip it back again.
        byte[] hed = null;
        byte[] pal = null;
        byte[][] tex_hed = null;
        byte[][] tex_data = null;
        byte[] cel = null;
        byte[] end = null;

        int total_size = 0;

        File directory = new File(folder);
        //System.out.println(directory.getAbsolutePath());
        File[] listOfFiles = null;

        // Grab the .hed file. This one won't be present in SP files
        listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {
                return (filename.endsWith(".HED") || (
                        filename.endsWith(".hed")
                        )); }
        });

        // Abort if there's more than one .hed file
        if (listOfFiles.length > 1){
            System.err.println("ERROR: More than one HED file!!");
            return;
        }
        else if (listOfFiles.length == 1){ // Only if we have 1 hed file
            hed = new byte[(int) listOfFiles[0].length()];
            f = new RandomAccessFile(listOfFiles[0].getAbsolutePath(), "r");
            f.read(hed);
            f.close();
        }
        

        // Grab the .pal file.
        listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {
                return (filename.endsWith(".PAL") || (
                        filename.endsWith(".pal")
                        )); }
        });

        // Abort if there's no .pal file or if there are more than one.
        if (listOfFiles.length != 1){
            System.err.println("ERROR: No PAL file or more than one!!");
            return;
        }
        else{
            pal = new byte[(int) listOfFiles[0].length()];
            f = new RandomAccessFile(listOfFiles[0].getAbsolutePath(), "r");
            f.read(pal);
            f.close();
        }


        // Grab the .cel file.
        listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {
                return (filename.endsWith(".CEL") || (
                        filename.endsWith(".cel")
                        )); }
        });

        // Abort if there's no .cel file or if there are more than one.
        if (listOfFiles.length != 1){
            System.err.println("ERROR: No CEL file or more than one!!");
            return;
        }
        else{
            cel = new byte[(int) listOfFiles[0].length()];
            f = new RandomAccessFile(listOfFiles[0].getAbsolutePath(), "r");
            f.read(cel);
            f.close();
        }


        // Prepare the END section
        end = new byte[16];

        // '45 4e 44 5f' - 'END_'
        end[0] = 0x45;
        end[1] = 0x4e;
        end[2] = 0x44;
        end[3] = 0x5f;


        // Grab the .hed2 files in the folder
        listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {  // I *THINK* they're in alphanumeric order
                return (filename.endsWith(".HED2") || (
                        filename.endsWith(".hed2")
                        )); }
        });

        // Abort if there are no hed2 files
        if (listOfFiles.length == 0){
            System.err.println("ERROR: No HED2 files!!");
            return;
        }
        else{
            tex_hed = new byte[listOfFiles.length][];
            for (int i = 0; i < tex_hed.length; i++){
                tex_hed[i] = new byte[(int) listOfFiles[i].length()];
                f = new RandomAccessFile(listOfFiles[i].getAbsolutePath(), "r");
                f.read(tex_hed[i]);
                f.close();
            }
        }


        // Find the BMP files in the folder
        listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {  // I *THINK* they're in alphanumeric order
                return (filename.endsWith(".BMP") || (
                        filename.endsWith(".bmp")
                        )); }
        });

        // Abort if there are no BMP files or if their number is different from the HED2 files
        if (listOfFiles.length != tex_hed.length){
            System.err.println("ERROR: The number of BMP files and HED2 files do NOT match!!");
            return;
        }
        else{
            // For each BMP file found:
            tex_data = new byte[listOfFiles.length][];

            for (int num = 0; num < tex_data.length; num++){
                f = new RandomAccessFile(listOfFiles[num].getAbsolutePath(), "r");

                byte[] header = new byte[54];
                byte[] aux = new byte[4];
                byte[] pixels = null;

                int offset;
                int width = 0;
                int height = 0;

                byte col_depth;

                f.read(header);

                aux[0] = header[13];
                aux[1] = header[12];
                aux[2] = header[11];
                aux[3] = header[10];
                offset = byteSeqToInt(aux);

                aux[0] = header[21];
                aux[1] = header[20];
                aux[2] = header[19];
                aux[3] = header[18];
                width = byteSeqToInt(aux);

                aux[0] = header[25];
                aux[1] = header[24];
                aux[2] = header[23];
                aux[3] = header[22];
                height = byteSeqToInt(aux);

                col_depth = header[28];


                // 1) Skip its header
                f.seek(offset);

                //System.out.println("Length of the file: " + listOfFiles[num].length() + " - Offset: " + offset);

                pixels = new byte[(int) listOfFiles[num].length() - offset];

                // 2) Grab the image data
                f.read(pixels);
                f.close();

                // 3) Turn it upside down
                byte[] pixels_R = pixels.clone();
                int dimX = width;
                if (col_depth == 4)
                    dimX = dimX / 2;

                for (int i = 0, j = pixels.length - dimX; i < pixels.length; i+=dimX, j-=dimX){
                    for (int k = 0; k < dimX; ++k){
                        //System.out.println("Length: " + pixels.length + " i: " + i + " j: " + j + " k: " + k);
                        pixels[i + k] = pixels_R[j + k];
                    }
                }


                // Turns out that if the image is stored as 4bpp, the nibbles have to be reversed
                if (col_depth == 4){
                    for (int i = 0; i < pixels.length; i++){
                        pixels[i] = (byte) ( ( (pixels[i] & 0x0f) << 4) | ( (pixels[i] & 0xf0) >> 4) );
                    }

                    // If that wasn't enough, in this mode the lines go like 1, 0, 3, 2, 5, 4, 7, 6 ... and so on
                    /*int dimX_4bpp = dimX / 2;

                    for (int i = 0; i < height; i += 2){
                        for (int j = 0; j < dimX_4bpp; j++){
                            byte byte_next_line = pixels[(i * dimX_4bpp) + j + dimX_4bpp];
                            pixels[(i * dimX_4bpp) + j + dimX_4bpp] = pixels[(i * dimX_4bpp) + j];
                            pixels[(i * dimX_4bpp) + j] = byte_next_line;
                        }
                    }*/
                }

                tex_data[num] = pixels;
            }
        }

        // Write all the found data into the specified file
        // * Order: hed, pal, textures, cel, END
        String path = folder + "/" + filename;

        f = new RandomAccessFile(path, "rw");

        if (hed != null){
            f.write(hed);
            
            total_size += hed.length;
        }

        f.write(pal);

        total_size += pal.length;

        for(int i = 0; i < tex_hed.length; i++){
            f.write(tex_hed[i]);
            f.write(tex_data[i]);

            total_size += tex_hed[i].length;
            total_size += tex_data[i].length;
        }

        f.write(cel);

        total_size += cel.length;

        f.write(end);

        total_size += end.length;

        if (total_size % 32 != 0){
            byte[] padding = new byte[32 - (total_size % 32)];

            f.write(padding);
        }

        f.close();

        System.out.println("File " + filename + " processed sucessfully.");
    }

    
    public static void writeOther(byte[] data, String extra, String extension){
        String path = pak_file + "_extract";
        File folder = new File(path);
        if (!folder.exists()){
            boolean success = folder.mkdir();
            if (!success){
                System.err.println("ERROR: Couldn't create folder.");
                return;
            }
        }

        // Create the file inside said folder
        String file_path = pak_file + extra + extension;
        path += "/" + file_path;
        try {
            RandomAccessFile other = new RandomAccessFile(path, "rw");
            
            // Truncate the file (in case we're overwriting)
            other.setLength(0);

            other.write(data);

            other.close();

            System.out.println(file_path + " saved successfully.");
            tex_counter++;
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + file_path);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    public static void writeBMP(String filename, byte[] CLUT, byte[] imageData, int width, int height, byte depth, int number){
        byte[] header = new byte[54];

        // Prepare the header
        // * All sizes are big endian

        // Byte 0: '42' (B) Byte 1: '4d' (M)
        header[0] = 0x42;
        header[1] = 0x4d;

        // Next 4 bytes: file size (header + CLUT + pixels)
        int file_size = 54 + CLUT.length + imageData.length;
        header[2] = (byte) (file_size & 0xff);
        header[3] = (byte) ((file_size >> 8) & 0xff);
        header[4] = (byte) ((file_size >> 16) & 0xff);
        header[5] = (byte) ((file_size >> 24) & 0xff);

        // Next 4 bytes: all 0
        header[6] = 0;
        header[7] = 0;
        header[8] = 0;
        header[9] = 0;

        // Next 4 bytes: offset to start of image (header + CLUT)
        int offset = file_size - imageData.length;
        header[10] = (byte) (offset & 0xff);
        header[11] = (byte) ((offset >> 8) & 0xff);
        header[12] = (byte) ((offset >> 16) & 0xff);
        header[13] = (byte) ((offset >> 24) & 0xff);

        // Next 4 bytes: 28 00 00 00
        header[14] = 40;
        header[15] = 0;
        header[16] = 0;
        header[17] = 0;

        // Next 4 bytes: Width
        header[18] = (byte) (width & 0xff);
        header[19] = (byte) ((width >> 8) & 0xff);
        header[20] = (byte) ((width >> 16) & 0xff);
        header[21] = (byte) ((width >> 24) & 0xff);

        // Next 4 bytes: Height
        header[22] = (byte) (height & 0xff);
        header[23] = (byte) ((height >> 8) & 0xff);
        header[24] = (byte) ((height >> 16) & 0xff);
        header[25] = (byte) ((height >> 24) & 0xff);

        // Next 2 bytes: 01 00 (number of planes in the image)
        header[26] = 1;
        header[27] = 0;

        // Next 2 bytes: bits per pixel ( 04 00 or 08 00 )
        header[28] = depth;
        header[29] = 0;

        // Next 4 bytes: 00 00 00 00 (compression)
        header[30] = 0;
        header[31] = 0;
        header[32] = 0;
        header[33] = 0;

        // Next 4 bytes: image size in bytes (pixels)
        header[34] = (byte) (imageData.length & 0xff);
        header[35] = (byte) ((imageData.length >> 8) & 0xff);
        header[36] = (byte) ((imageData.length >> 16) & 0xff);
        header[37] = (byte) ((imageData.length >> 24) & 0xff);

        // Next 12 bytes: all 0 (horizontal and vertical resolution, number of colours)
        header[38] = 0;
        header[39] = 0;
        header[40] = 0;
        header[41] = 0;
        header[42] = 0;
        header[43] = 0;
        header[44] = 0;
        header[45] = 0;
        header[46] = 0;
        header[47] = 0;
        header[48] = 0;
        header[49] = 0;

        // Next 4 bytes: important colours (= number of colours)
        header[50] = 0;
        header[51] = (byte)(CLUT.length / 4);
        header[52] = 0;
        header[53] = 0;

        // Check if folder with the name of the pak_file exists. If not, create it.
        String path = pak_file + "_extract";
        File folder = new File(path);
        if (!folder.exists()){
            boolean success = folder.mkdir();
            if (!success){
                System.err.println("ERROR: Couldn't create folder.");
                return;
            }
        }

        // Create the bmp file inside said folder
        String file_path = filename + "_" + number + ".bmp";
        path += "/" + file_path;
        try {
            RandomAccessFile bmp = new RandomAccessFile(path, "rw");
            
            // Truncate the file (in case we're overwriting)
            bmp.setLength(0);

            bmp.write(header);
            bmp.write(CLUT);
            bmp.write(imageData);
            
            bmp.close();

            System.out.println(file_path + " saved successfully.");
            tex_counter++;
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + file_path);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
