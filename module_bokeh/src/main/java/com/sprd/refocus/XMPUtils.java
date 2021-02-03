package com.sprd.refocus;

import android.util.Log;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPIterator;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.options.SerializeOptions;
import com.adobe.xmp.properties.XMPPropertyInfo;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class XMPUtils {
    private static final String TAG = "XmpUtil";

    private static final String XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0";
    private static final String XMP_EXTENSION_HEADER = "http://ns.adobe.com/xmp/extension/\0";
    private static final String XMP_HAS_EXTENSION = "HasExtendedXMP";
    private static final String XMP_NOTE_NAMESPACE = "http://ns.adobe.com/xmp/note/";
    private static final String EXTENDED_XMP_HEADER_SIGNATURE = "http://ns.adobe.com/xmp/extension/\0";

    private static final int XMP_EXTENSION_HEADER_GUID_SIZE = XMP_EXTENSION_HEADER.length() + 32 + 1; // 32 byte GUID + 1 byte null termination.
    private static final int XMP_EXTENSION_HEADER_OFFSET = 7;

    private static final int M_SOI = 0xd8; // File start marker.
    private static final int M_APP1 = 0xe1; // Marker for EXIF or XMP.
    private static final int M_SOS = 0xda; // Image data marker.
    private static final int MAX_XMP_BUFFER_SIZE = 65502;
    private static final int XMP_HEADER_SIZE = 29;
    private static final int MAX_EXTENDED_XMP_BUFFER_SIZE = 65000;
    private static final int EXTEND_XMP_HEADER_SIZE = 75;

    private static class Section {
        public int marker;
        public int length;
        public byte[] data;
    }


    public static FullXMPMeta read(String path) {
        if (!path.toLowerCase().endsWith(".jpg")
                && !path.toLowerCase().endsWith(".jpeg")) {
            Log.d(TAG, "XMP parse: only jpeg file is supported");
            return null;
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(path);
            return read(inputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static FullXMPMeta read(InputStream is) {

        FullXMPMeta fullXMPMeta = new FullXMPMeta();
        List<Section> sections = parse(is, true, false);
        if (sections == null) {
            return null;
        }
        fullXMPMeta.setmXMPMeta(parseFirstValidXMPSection(sections));
        if (fullXMPMeta.getmXMPMeta().doesPropertyExist(XMPConst.NS_XMP_NOTE, XMP_HAS_EXTENSION)) {
            String extensionName = null;
            try {
                extensionName = (String) fullXMPMeta.getmXMPMeta().getProperty(
                        XMPConst.NS_XMP_NOTE, XMP_HAS_EXTENSION).getValue();
                if (checkExtendedSectionExists(sections, extensionName)) {
                    fullXMPMeta.setmExtendedXMPMeta(parseExtendedXMPSections(sections, extensionName));
                }
            } catch (XMPException e) {
                e.printStackTrace();
            }
        }
        return fullXMPMeta;
    }

    public static XMPMeta read(InputStream is, boolean skipExtendedContent) {
        List<Section> sections = parse(is, true, skipExtendedContent);
        if (sections == null) {
            return null;
        }

        XMPMeta xmpMeta = parseFirstValidXMPSection(sections);
        if (xmpMeta == null ||
                !xmpMeta.doesPropertyExist(XMPConst.NS_XMP_NOTE, XMP_HAS_EXTENSION)) {
            return xmpMeta;
        }

        String extensionName = null;
        try {
            extensionName = (String) xmpMeta.getProperty(
                    XMPConst.NS_XMP_NOTE, XMP_HAS_EXTENSION).getValue();
        } catch (XMPException e) {
            e.printStackTrace();
            return null;
        }

        if (skipExtendedContent) {
            if (!checkExtendedSectionExists(sections, extensionName)) {
                // The main XMP section referenced an extended section that is not present.
                // This is an error.
                return null;
            }
            return xmpMeta;
        }

        XMPMeta xmpExtended = parseExtendedXMPSections(sections, extensionName);
        if (xmpExtended == null) {
            // The main XMP section referenced an extended section that is not present.
            // This is an error.
            return null;
        }
        // Merge the extended properties into the main one.
        try {
            XMPIterator iterator = xmpExtended.iterator();
            while (true) {
                XMPPropertyInfo info = (XMPPropertyInfo) iterator.next();
                if (info.getPath() != null) {
                    xmpMeta.setProperty(info.getNamespace(), info.getPath(),
                            info.getValue(), info.getOptions());
                }
            }
        } catch (Exception e) {
            // Catch XMPException and NoSuchElementException.
        }
        return xmpMeta;
    }

    /**
     * Parses the JPEG image file. If readMetaOnly is true, only keeps the EXIF
     * and XMP sections (with marker M_APP1) and ignore others; otherwise, keep
     * all sections. The last section with image data will have -1 length.
     *
     * @param is                  Input image data stream
     * @param readMetaOnly        Whether only reads the metadata in jpg
     * @param skipExtendedContent Whether to skip the content of extended sections
     * @return The parse result
     */
    private static List<Section> parse(InputStream is, boolean readMetaOnly,
                                       boolean skipExtendedContent) {
        List<Section> sections = new ArrayList<Section>();
        if (is == null) {
            return sections;
        }
        int k;
        try {
            if ((k = is.read()) != 0xff || (k = is.read()) != M_SOI) {
                return sections;
            }
            int c;
            while ((c = is.read()) != -1) {
                if (c != 0xff) {
                    return sections;
                }
                // Skip padding bytes.
                while ((c = is.read()) == 0xff) {
                }
                if (c == -1) {
                    return sections;
                }
                int marker = c;
                if (marker == M_SOS) {
                    // M_SOS indicates the image data will follow and no metadata after
                    // that, so read all data at one time.
                    if (!readMetaOnly) {
                        Section section = new Section();
                        section.marker = marker;
                        section.length = -1;
                        section.data = new byte[is.available()];
                        is.read(section.data, 0, section.data.length);
                        sections.add(section);
                    }
                    return sections;
                }
                int lh = is.read();
                int ll = is.read();
                if (lh == -1 || ll == -1) {
                    return sections;
                }
                int length = lh << 8 | ll;
                if (!readMetaOnly || marker == M_APP1) {
                    sections.add(readSection(is, length, marker, skipExtendedContent));
                } else {
                    // Skip this section since all EXIF/XMP meta will be in M_APP1
                    // section.
                    is.skip(length - 2);
                }
            }
            return sections;
        } catch (IOException e) {
            System.out.println("Could not parse file." + e);
            return sections;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }
    }

    /**
     * Checks whether the byte array has XMP header. The XMP section contains
     * a fixed length header XMP_HEADER.
     *
     * @param data   XMP metadata
     * @param header The header to look for
     */
    private static boolean hasHeader(byte[] data, String header) {
        if (data.length < header.length()) {
            return false;
        }
        try {
            byte[] buffer = new byte[header.length()];
            System.arraycopy(data, 0, buffer, 0, header.length());
            if (new String(buffer, "UTF-8").equals(header)) {
                return true;
            }
        } catch (UnsupportedEncodingException e) {
            return false;
        }
        return false;
    }

    private static int count;

    private static Section readSection(InputStream is, int length,
                                       int marker, boolean skipExtendedContent) throws IOException {
        if (length - 2 < XMP_EXTENSION_HEADER_GUID_SIZE || !skipExtendedContent) {
            Section section = new Section();
            section.marker = marker;
            section.length = length;
            section.data = new byte[length - 2];
            is.read(section.data, 0, length - 2);
            count++;
            return section;
        }

        byte[] header = new byte[XMP_EXTENSION_HEADER_GUID_SIZE];
        is.read(header, 0, header.length);
        if (hasHeader(header, XMP_EXTENSION_HEADER) && skipExtendedContent) {
            Section section = new Section();
            section.marker = marker;
            section.length = header.length + 2;
            section.data = header;
            is.skip(length - 2 - header.length);
            return section;
        }

        Section section = new Section();
        section.marker = marker;
        section.length = length;
        section.data = new byte[length - 2];
        System.arraycopy(header, 0, section.data, 0, header.length);
        is.read(section.data, header.length, length - 2 - header.length);
        return section;
    }


    /**
     * Parses the first valid XMP section. Any other valid XMP section will be
     * ignored.
     *
     * @param sections The list of sections parse
     * @return The parsed XMPMeta object
     */
    private static XMPMeta parseFirstValidXMPSection(List<Section> sections) {
        for (Section section : sections) {
            if (hasHeader(section.data, XMP_HEADER)) {
                int end = getXMPContentEnd(section.data);
                byte[] buffer = new byte[end - XMP_HEADER.length()];
                System.arraycopy(
                        section.data, XMP_HEADER.length(), buffer, 0, buffer.length);
                try {
                    XMPMeta result = XMPMetaFactory.parseFromBuffer(buffer);
                    return result;
                } catch (XMPException e) {
                    System.out.println("XMP parse error " + e);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Checks there is an extended section with the given name.
     *
     * @param sections    The list of sections to parse
     * @param sectionName The name of the extended sections
     * @return Whether there is an extended section with the given name
     */
    private static boolean checkExtendedSectionExists(List<Section> sections,
                                                      String sectionName) {
        String extendedHeader = XMP_EXTENSION_HEADER + sectionName + "\0";
        for (Section section : sections) {
            if (hasHeader(section.data, extendedHeader)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the extended XMP sections with the given name. All other sections
     * will be ignored.
     *
     * @param sections    The list of sections to parse
     * @param sectionName The name of the extended sections
     * @return The parsed XMPMeta object
     */
    private static XMPMeta parseExtendedXMPSections(List<Section> sections,
                                                    String sectionName) {
        String extendedHeader = XMP_EXTENSION_HEADER + sectionName + "\0";

        // Compute the size of the buffer to parse the extended sections.
        List<Section> xmpSections = new ArrayList<Section>();
        List<Integer> xmpStartOffset = new ArrayList<Integer>();
        List<Integer> xmpEndOffset = new ArrayList<Integer>();
        int bufferSize = 0;
        for (Section section : sections) {
            if (hasHeader(section.data, extendedHeader)) {
                int startOffset = extendedHeader.length() + XMP_EXTENSION_HEADER_OFFSET;
                int endOffset = section.data.length;
                bufferSize += Math.max(0, section.data.length - startOffset);
                xmpSections.add(section);
                xmpStartOffset.add(startOffset);
                xmpEndOffset.add(endOffset);
            }
        }
        if (bufferSize == 0) {
            return null;
        }

        // Copy all the relevant sections' data into a buffer.
        byte buffer[] = new byte[bufferSize];
        int offset = 0;
        for (int i = 0; i < xmpSections.size(); ++i) {
            Section section = xmpSections.get(i);
            int startOffset = xmpStartOffset.get(i);
            int endOffset = xmpEndOffset.get(i);
            int length = endOffset - startOffset;
            System.arraycopy(
                    section.data, startOffset, buffer, offset, length);
            offset += length;
        }

        XMPMeta xmpExtended = null;
        try {
            xmpExtended = XMPMetaFactory.parseFromBuffer(buffer);
        } catch (XMPException e) {
            System.out.println("Extended XMP parse error " + e);
            return null;
        }
        return xmpExtended;
    }


    public static XMPMeta extractXMPMeta(String filename) {
        if (!filename.toLowerCase().endsWith(".jpg")
                && !filename.toLowerCase().endsWith(".jpeg")) {
            Log.d(TAG, "XMP parse: only jpeg file is supported");
            return null;
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filename);
            return extractXMPMeta(inputStream);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not read file: " + filename, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static XMPMeta extractXMPMeta(InputStream is) {
        List<Section> sections = parse(is, true);
        if (sections == null) {
            return null;
        }
        // Now we don't support extended xmp.
        for (Section section : sections) {
            if (hasXMPHeader(section.data)) {
                int end = getXMPContentEnd(section.data);
                byte[] buffer = new byte[end - XMP_HEADER_SIZE];
                System.arraycopy(
                        section.data, XMP_HEADER_SIZE, buffer, 0, buffer.length);
                try {
                    XMPMeta result = XMPMetaFactory.parseFromBuffer(buffer);
                    return result;
                } catch (XMPException e) {
                    Log.d(TAG, "XMP parse error", e);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Gets the end of the xmp meta content. If there is no packet wrapper,
     * return data.length, otherwise return 1 + the position of last '>'
     * without '?' before it.
     * Usually the packet wrapper end is "<?xpacket end="w"?> but
     * javax.xml.parsers.DocumentBuilder fails to parse it in android.
     *
     * @param data xmp metadata bytes.
     * @return The end of the xmp metadata content.
     */
    private static int getXMPContentEnd(byte[] data) {
        for (int i = data.length - 1; i >= 1; --i) {
            if (data[i] == '>') {
                if (data[i - 1] != '?') {
                    return i + 1;
                }
            }
        }
        // It should not reach here for a valid xmp meta.
        return data.length;
    }

    /**
     * Parses the jpeg image file. If readMetaOnly is true, only keeps the Exif
     * and XMP sections (with marker M_APP1) and ignore others; otherwise, keep
     * all sections. The last section with image data will have -1 length.
     *
     * @param is           Input image data stream.
     * @param readMetaOnly Whether only reads the metadata in jpg.
     * @return The parse result.
     */
    private static List<Section> parse(InputStream is, boolean readMetaOnly) {
        try {
            if (is.read() != 0xff || is.read() != M_SOI) {
                return null;
            }
            List<Section> sections = new ArrayList<Section>();
            int c;
            while ((c = is.read()) != -1) {
                if (c != 0xff) {
                    return null;
                }
                // Skip padding bytes.
                while ((c = is.read()) == 0xff) {
                }
                if (c == -1) {
                    return null;
                }
                int marker = c;
                if (marker == M_SOS) {
                    // M_SOS indicates the image data will follow and no metadata after
                    // that, so read all data at one time.
                    if (!readMetaOnly) {
                        Section section = new Section();
                        section.marker = marker;
                        section.length = -1;
                        section.data = new byte[is.available()];
                        is.read(section.data, 0, section.data.length);
                        sections.add(section);
                    }
                    return sections;
                }
                int lh = is.read();
                int ll = is.read();
                if (lh == -1 || ll == -1) {
                    return null;
                }
                int length = lh << 8 | ll;
                if (!readMetaOnly || c == M_APP1) {
                    Section section = new Section();
                    section.marker = marker;
                    section.length = length;
                    section.data = new byte[length - 2];
                    is.read(section.data, 0, length - 2);
                    sections.add(section);
                } else {
                    // Skip this section since all exif/xmp meta will be in M_APP1
                    // section.
                    is.skip(length - 2);
                }
            }
            return sections;
        } catch (IOException e) {
            Log.d(TAG, "Could not parse file.", e);
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }
    }

    public static boolean writeXMPMeta(String filepath,
                                       XMPMeta meta) {
        FileOutputStream outputStream = null;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filepath);
            outputStream = new FileOutputStream(filepath);
            return writeXMPMeta(inputStream, outputStream, meta);
        } catch (Exception e) {
            Log.e(TAG, "Could not writeXMPMeta: " + e);
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean writeXMPMeta(byte[] inputdata, OutputStream outputStream,
                                       XMPMeta meta) {
        ByteArrayInputStream inputStream = null;
        inputStream = new ByteArrayInputStream(inputdata);
        boolean ret = writeXMPMeta(inputStream, outputStream, meta);
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }


    public static boolean writeXMPMeta(InputStream inputStream, OutputStream outputStream,
                                       XMPMeta meta) {
        List<Section> sections = parse(inputStream, true, true);
        Log.d(TAG, "read sections size:" + sections.size());
        sections = insertXMPSection(sections, meta);
        if (sections == null) {
            Log.d(TAG, "final section is null");
            return false;
        }
        try {
            // Overwrite the image file with the new meta data.
            writeJpegFile(outputStream, sections);
        } catch (IOException e) {
            Log.d(TAG, "Write to stream failed", e);
            return false;
        }
        return true;
    }

    private static List<Section> insertXMPSection(
            List<Section> sections, XMPMeta meta) {
        if (sections == null) {
            return null;
        }
        byte[] buffer;
        try {
            SerializeOptions options = new SerializeOptions();
            options.setUseCompactFormat(true);
            // We have to omit packet wrapper here because
            // javax.xml.parsers.DocumentBuilder
            // fails to parse the packet end <?xpacket end="w"?> in android.
            options.setOmitPacketWrapper(true);
            buffer = XMPMetaFactory.serializeToBuffer(meta, options);
        } catch (XMPException e) {
            Log.d(TAG, "Serialize xmp failed", e);
            return null;
        }
        /*if (buffer.length > MAX_XMP_BUFFER_SIZE) {
            // Do not support extended xmp now.
            Log.d(TAG, "buffer.length:" + buffer.length);
            return null;
        }*/
        // The XMP section starts with XMP_HEADER and then the real xmp data.
        byte[] xmpdata = new byte[buffer.length + XMP_HEADER_SIZE];
        System.arraycopy(XMP_HEADER.getBytes(), 0, xmpdata, 0, XMP_HEADER_SIZE);
        System.arraycopy(buffer, 0, xmpdata, XMP_HEADER_SIZE, buffer.length);
        Section xmpSection = new Section();
        xmpSection.marker = M_APP1;
        // Adds the length place (2 bytes) to the section length.
        xmpSection.length = xmpdata.length + 2;
        xmpSection.data = xmpdata;

        for (int i = 0; i < sections.size(); ++i) {
            // If we can find the old xmp section, replace it with the new one.
            if (sections.get(i).marker == M_APP1
                    && hasXMPHeader(sections.get(i).data)) {
                // Replace with the new xmp data.
                sections.set(i, xmpSection);
                return sections;
            }
        }
        // If the first section is Exif, insert XMP data before the second section,
        // otherwise, make xmp data the first section.
        List<Section> newSections = new ArrayList<Section>();
        int position = (sections.get(0).marker == M_APP1) ? 1 : 0;
        newSections.addAll(sections.subList(0, position));
        newSections.add(xmpSection);
        newSections.addAll(sections.subList(position, sections.size()));
        return newSections;
    }

    private static boolean hasXMPHeader(byte[] data) {
        if (data.length < XMP_HEADER_SIZE) {
            return false;
        }
        try {
            byte[] header = new byte[XMP_HEADER_SIZE];
            System.arraycopy(data, 0, header, 0, XMP_HEADER_SIZE);
            if (new String(header, "UTF-8").equals(XMP_HEADER)) {
                return true;
            }
        } catch (UnsupportedEncodingException e) {
            return false;
        }
        return false;
    }

    private static void writeJpegFile(OutputStream os, List<Section> sections)
            throws IOException {
        // Writes the jpeg file header.
        os.write(0xff);
        os.write(M_SOI);
        for (Section section : sections) {
            os.write(0xff);
            os.write(section.marker);
            if (section.length > 0) {
                // It's not the image data.
                int lh = section.length >> 8;
                int ll = section.length & 0xff;
                os.write(lh);
                os.write(ll);
            }
            os.write(section.data);
        }
    }

    public static boolean writeXMPMeta(InputStream inputStream, OutputStream outputStream,
                                       XMPMeta standardMeta, XMPMeta extendedMeta) {
        byte[] buffer;
        try {
            SerializeOptions options = new SerializeOptions();
            options.setUseCompactFormat(true);
            // We have to omit packet wrapper here because
            // javax.xml.parsers.DocumentBuilder
            // fails to parse the packet end <?xpacket end="w"?> in android.
            options.setOmitPacketWrapper(true);
            buffer = XMPMetaFactory.serializeToBuffer(extendedMeta, options);
        } catch (XMPException e) {
            Log.d(TAG, "Serialize extended xmp failed", e);
            return false;
        }

        String guid = getGUID(buffer);
        try {
            standardMeta.setProperty(XMP_NOTE_NAMESPACE, "HasExtendedXMP", guid);
        } catch (XMPException exception) {
            Log.d(TAG, "set XMPMeta Property", exception);
            return false;
        }
        List<Section> sections = parse(inputStream, false);
        List<Section> xmpSections = new ArrayList<Section>();
        Section standardXmpSection = createStandardXMPSection(standardMeta);
        if (standardXmpSection == null) {
            Log.e(TAG, "create standard meta section error");
            return false;
        }
        xmpSections.add(standardXmpSection);

        List<Section> extendedSections = splitExtendXMPMeta(buffer, guid);
        xmpSections.addAll(extendedSections);
        sections = insertXMPSection(sections, xmpSections);
        if (sections == null) {
            Log.d(TAG, "Insert XMP fialed");
            return false;
        }
        try {
            // Overwrite the image file with the new meta data.
            writeJpegFile(outputStream, sections);
        } catch (IOException e) {
            Log.d(TAG, "Write to stream failed", e);
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }
        return true;
    }

    private static String getGUID(byte[] src) {
        StringBuilder builder = new StringBuilder();
        try {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            digester.update(src);
            byte[] digest = digester.digest();

            Formatter formatter = new Formatter(builder);
            for (int i = 0; i < digest.length; ++i) {
                formatter.format("%02x", ((256 + digest[i]) % 256));
            }
        } catch (NoSuchAlgorithmException exception) {
            Log.d(TAG, "get md5 instance failure" + exception);
            return null;
        }

        return builder.toString().toUpperCase();
    }

    private static Section createStandardXMPSection(XMPMeta meta) {
        byte[] buffer;
        try {
            SerializeOptions options = new SerializeOptions();
            options.setUseCompactFormat(true);
            // We have to omit packet wrapper here because
            // javax.xml.parsers.DocumentBuilder
            // fails to parse the packet end <?xpacket end="w"?> in android.
            options.setOmitPacketWrapper(true);
            buffer = XMPMetaFactory.serializeToBuffer(meta, options);
        } catch (XMPException e) {
            Log.d(TAG, "Serialize xmp failed", e);
            return null;
        }
        if (buffer.length > MAX_XMP_BUFFER_SIZE) {
            Log.e(TAG, "exceed max size");
            return null;
        }
        // The XMP section starts with XMP_HEADER and then the real xmp data.
        byte[] xmpdata = new byte[buffer.length + XMP_HEADER_SIZE];
        System.arraycopy(XMP_HEADER.getBytes(), 0, xmpdata, 0, XMP_HEADER_SIZE);
        System.arraycopy(buffer, 0, xmpdata, XMP_HEADER_SIZE, buffer.length);
        Section xmpSection = new Section();
        xmpSection.marker = M_APP1;
        // Adds the length place (2 bytes) to the section length.
        xmpSection.length = xmpdata.length + 2;
        xmpSection.data = xmpdata;

        return xmpSection;
    }

    /**
     * Split extendXMPMeta to multiple marker segments
     *
     * @param extendedXMPMetaBytes serialized extended XMP
     * @param guid                 Is a 128-bit MD5 digest of the full ExtendedXMP serialization,
     *                             stored as a 32-byte ASCII hex string
     * @return split result
     */
    private static List<Section> splitExtendXMPMeta(byte[] extendedXMPMetaBytes, String guid) {
        List<Section> sections = new ArrayList<Section>();
     /*
    The extended XMP JPEG marker segment content holds:
    - a signature string, "http://ns.adobe.com/xmp/extension/\0"
    - a 128 bit GUID stored as a 32 byte ASCII hex string
    - a UInt32 full length of the entire extended XMP
    - a UInt32 offset for this portion of the extended XMP
    - the UTF-8 text for this portion of the extended XMP
     */
        int splitNum = extendedXMPMetaBytes.length / MAX_EXTENDED_XMP_BUFFER_SIZE;
        byte[] portion = new byte[MAX_EXTENDED_XMP_BUFFER_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(extendedXMPMetaBytes);
        Section extendedXmpSection = null;

        byte[] headerBytes = new byte[EXTEND_XMP_HEADER_SIZE];
        int index = 0;
        System.arraycopy(EXTENDED_XMP_HEADER_SIGNATURE.getBytes(), 0, headerBytes, 0, EXTENDED_XMP_HEADER_SIGNATURE.length());
        index += EXTENDED_XMP_HEADER_SIGNATURE.length();

        System.arraycopy(guid.getBytes(), 0, headerBytes, index, guid.length());
        index += guid.length();

        Log.d(TAG, "buffer.length=" + extendedXMPMetaBytes.length);
        byte[] fullLengthBytes = new byte[4];
        ByteBuffer intBuffer = ByteBuffer.wrap(fullLengthBytes);
        intBuffer.putInt(0, extendedXMPMetaBytes.length);
        System.arraycopy(fullLengthBytes, 0, headerBytes, index, 4);
        index += 4;

        byte[] offsetBytes = new byte[4];
        ByteBuffer offsetBuffer = ByteBuffer.wrap(offsetBytes);
        for (int i = 0; i < splitNum; ++i) {
            offsetBuffer.putInt(0, i * MAX_EXTENDED_XMP_BUFFER_SIZE);
            System.arraycopy(offsetBytes, 0, headerBytes, index, 4);

            byteBuffer.get(portion);
            extendedXmpSection = createSection(portion, headerBytes);
            sections.add(extendedXmpSection);
        }

        int remainSize = extendedXMPMetaBytes.length - splitNum * MAX_EXTENDED_XMP_BUFFER_SIZE;
        if (remainSize > 0) {
            offsetBuffer.putInt(0, splitNum * MAX_EXTENDED_XMP_BUFFER_SIZE);
            System.arraycopy(offsetBytes, 0, headerBytes, index, 4);

            byte[] remain = new byte[remainSize];
            byteBuffer.get(remain);
            extendedXmpSection = createSection(remain, headerBytes);
            sections.add(extendedXmpSection);
        }

        return sections;
    }


    private static Section createSection(byte[] portionOfExtendedMeta, byte[] headerBytes) {

        if (portionOfExtendedMeta.length > MAX_EXTENDED_XMP_BUFFER_SIZE) {
            // Do not support extended xmp now.
            Log.e(TAG, "createSection fail exceed max size");
            return null;
        }

        byte[] xmpdata = new byte[portionOfExtendedMeta.length + 75];
        System.arraycopy(headerBytes, 0, xmpdata, 0, headerBytes.length);

        System.arraycopy(portionOfExtendedMeta, 0, xmpdata, headerBytes.length, portionOfExtendedMeta.length);
        Section xmpSection = new Section();
        xmpSection.marker = M_APP1;
        // Adds the length place (2 bytes) to the section length.
        xmpSection.length = xmpdata.length + 2;
        xmpSection.data = xmpdata;
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(xmpdata);
        //Log.d(TAG, "fullLength=" + byteBuffer2.getInt(67) + " offset=" + byteBuffer2.getInt(71));
        return xmpSection;
    }

    private static List<Section> insertXMPSection(
            List<Section> sections, List<Section> xmpSections) {
        if (sections == null || sections.size() <= 1) {
            return null;
        }

        // If the first section is Exif, insert XMP data before the second section,
        // otherwise, make xmp data the first section.
        List<Section> newSections = new ArrayList<Section>();
        int position = (sections.get(0).marker == M_APP1) ? 1 : 0;
        newSections.addAll(sections.subList(0, position));
        newSections.addAll(xmpSections);
        newSections.addAll(sections.subList(position, sections.size()));
        return newSections;
    }
}
