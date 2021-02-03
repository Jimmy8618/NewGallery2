/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.exif;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

class ExifModifier {
    public static final String TAG = "ExifModifier";
    public static final boolean DEBUG = false;
    /**
     * 可读可写文件的buffer, 如内部存储中的文件
     */
    private ByteBuffer mWritableBuffer;
    /**
     * 文件fd
     */
    private ParcelFileDescriptor mfd;
    /**
     * 文件uri
     */
    private Uri mUri;
    /**
     * 可读可写文件
     */
    private RandomAccessFile mRandomAccessFile;
    /**
     * 文件输入流
     */
    private FileInputStream mFileInputStream;
    /**
     * 文件输出流
     */
    private FileOutputStream mFileOutputStream;

    private final ExifData mTagToModified;
    private final List<TagOffset> mTagOffsets = new ArrayList<TagOffset>();
    private final ExifInterface mInterface;
    private int mOffsetBase;

    private static class TagOffset {
        final int mOffset;
        final ExifTag mTag;

        TagOffset(ExifTag tag, int offset) {
            mTag = tag;
            mOffset = offset;
        }
    }

    ExifModifier(ContentResolver resolver, Uri uri, long exifSize, ExifInterface iRef) throws IOException,
            ExifInvalidFormatException {
        mUri = uri;

        if (mUri != null) {
            mFileInputStream = (FileInputStream) resolver.openInputStream(uri);
            mFileOutputStream = (FileOutputStream) resolver.openOutputStream(uri);
            mWritableBuffer = mFileInputStream.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, exifSize);
        }

        if (mWritableBuffer == null) {
            throw new IOException("mWritableBuffer is null.");
        }
        mOffsetBase = mWritableBuffer.position();
        mInterface = iRef;
        InputStream is = null;
        try {
            is = new ByteBufferInputStream(mWritableBuffer);
            // Do not require any IFD;
            ExifParser parser = ExifParser.parse(is, mInterface);
            mTagToModified = new ExifData(parser.getByteOrder());
            mOffsetBase += parser.getTiffStartPosition();
            mWritableBuffer.position(0);
        } finally {
            ExifInterface.closeSilently(is);
        }
    }

    ExifModifier(String filePath, long exifSize, ParcelFileDescriptor fd, ExifInterface iRef) throws IOException,
            ExifInvalidFormatException {
        mfd = fd;

        if (fd != null) {
            //若是外部存储中的图片, 无法使用 RandomAccessFile 处理, 这里只生成 只读的 mWritableBuffer
            mFileInputStream = new FileInputStream(fd.getFileDescriptor());
            mWritableBuffer = mFileInputStream.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, exifSize);
        } else {
            //若是内部存储中的图片, 则直接使用 RandomAccessFile 处理, 生成 可读可写的 mWritableBuffer, 文件映射, 处理速度快
            mRandomAccessFile = new RandomAccessFile(filePath, "rw");
            mWritableBuffer = mRandomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, exifSize);
        }

        if (mWritableBuffer == null) {
            throw new IOException("mWritableBuffer is null.");
        }
        mOffsetBase = mWritableBuffer.position();
        mInterface = iRef;
        InputStream is = null;
        try {
            is = new ByteBufferInputStream(mWritableBuffer);
            // Do not require any IFD;
            ExifParser parser = ExifParser.parse(is, mInterface);
            mTagToModified = new ExifData(parser.getByteOrder());
            mOffsetBase += parser.getTiffStartPosition();
            mWritableBuffer.position(0);
        } finally {
            ExifInterface.closeSilently(is);
        }
    }

    private ByteOrder getByteOrder() {
        return mTagToModified.getByteOrder();
    }

    protected boolean commit() throws IOException, ExifInvalidFormatException {
        InputStream is = null;
        try {
            is = new ByteBufferInputStream(mWritableBuffer);
            int flag = 0;
            IfdData[] ifdDatas = new IfdData[]{
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_0),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_1),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_EXIF),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_GPS)
            };

            if (ifdDatas[IfdId.TYPE_IFD_0] != null) {
                flag |= ExifParser.OPTION_IFD_0;
            }
            if (ifdDatas[IfdId.TYPE_IFD_1] != null) {
                flag |= ExifParser.OPTION_IFD_1;
            }
            if (ifdDatas[IfdId.TYPE_IFD_EXIF] != null) {
                flag |= ExifParser.OPTION_IFD_EXIF;
            }
            if (ifdDatas[IfdId.TYPE_IFD_GPS] != null) {
                flag |= ExifParser.OPTION_IFD_GPS;
            }
            if (ifdDatas[IfdId.TYPE_IFD_INTEROPERABILITY] != null) {
                flag |= ExifParser.OPTION_IFD_INTEROPERABILITY;
            }

            ExifParser parser = ExifParser.parse(is, flag, mInterface);
            int event = parser.next();
            IfdData currIfd = null;
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_START_OF_IFD:
                        currIfd = ifdDatas[parser.getCurrentIfd()];
                        if (currIfd == null) {
                            parser.skipRemainingTagsInCurrentIfd();
                        }
                        break;
                    case ExifParser.EVENT_NEW_TAG:
                        ExifTag oldTag = parser.getTag();
                        ExifTag newTag = null;
                        if (currIfd != null) {
                            newTag = currIfd.getTag(oldTag.getTagId());
                        }
                        if (newTag != null) {
                            if (newTag.getComponentCount() != oldTag.getComponentCount()
                                    || newTag.getDataType() != oldTag.getDataType()) {
                                return false;
                            } else {
                                mTagOffsets.add(new TagOffset(newTag, oldTag.getOffset()));
                                currIfd.removeTag(oldTag.getTagId());
                                if (currIfd.getTagCount() == 0) {
                                    parser.skipRemainingTagsInCurrentIfd();
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
                event = parser.next();
            }
            for (IfdData ifd : ifdDatas) {
                if (ifd != null && ifd.getTagCount() > 0) {
                    return false;
                }
            }
            modify();
        } finally {
            ExifInterface.closeSilently(is);
            ExifInterface.closeSilently(mRandomAccessFile);
            ExifInterface.closeSilently(mFileInputStream);
            ExifInterface.closeSilently(mFileOutputStream);
        }
        return true;
    }

    private void modify() throws IOException {
        mWritableBuffer.order(getByteOrder());

        if (mfd != null) {
            //外部存储图片, 由于没有写权限, 需要作转换处理
            //创建一个 ByteBuffer, 大小和 mWritableBuffer 一致
            ByteBuffer outBuffer = ByteBuffer.allocateDirect(mWritableBuffer.limit());
            outBuffer.order(mWritableBuffer.order());
            //保存一下 mWritableBuffer 起始 position 位置
            int position = mWritableBuffer.position();
            //将 mWritableBuffer position 位置设置为 0
            mWritableBuffer.position(0);
            //将 mWritableBuffer 内容放入 outBuffer 中
            outBuffer.put(mWritableBuffer);
            //将 position 位置设回来
            mWritableBuffer.position(position);
            outBuffer.position(position);
            //写 tag 信息到 outBuffer 中
            for (TagOffset tagOffset : mTagOffsets) {
                writeTagValue(tagOffset.mTag, tagOffset.mOffset, outBuffer);
            }
            //将 outBuffer position 位置设为 0
            outBuffer.position(0);
            //往输出管道中写入 outBuffer 数据
            mFileOutputStream = new FileOutputStream(mfd.getFileDescriptor());
            mFileOutputStream.getChannel().write(outBuffer, 0);
        } else if (mUri != null) {
            //创建一个 ByteBuffer, 大小和 mWritableBuffer 一致
            ByteBuffer outBuffer = ByteBuffer.allocateDirect(mWritableBuffer.limit());
            outBuffer.order(mWritableBuffer.order());
            //保存一下 mWritableBuffer 起始 position 位置
            int position = mWritableBuffer.position();
            //将 mWritableBuffer position 位置设置为 0
            mWritableBuffer.position(0);
            //将 mWritableBuffer 内容放入 outBuffer 中
            outBuffer.put(mWritableBuffer);
            //将 position 位置设回来
            mWritableBuffer.position(position);
            outBuffer.position(position);
            //写 tag 信息到 outBuffer 中
            for (TagOffset tagOffset : mTagOffsets) {
                writeTagValue(tagOffset.mTag, tagOffset.mOffset, outBuffer);
            }
            //将 outBuffer position 位置设为 0
            outBuffer.position(0);
            //往输出管道中写入 outBuffer 数据
            mFileOutputStream.getChannel().write(outBuffer, 0);
        } else {
            //内部存储的图片, 直接使用 mWritableBuffer, 处理速度快
            for (TagOffset tagOffset : mTagOffsets) {
                writeTagValue(tagOffset.mTag, tagOffset.mOffset, mWritableBuffer);
            }
        }
    }

    private void writeTagValue(ExifTag tag, int offset, ByteBuffer byteBuffer) {
        if (DEBUG) {
            Log.v(TAG, "modifying tag to: \n" + tag.toString());
            Log.v(TAG, "at offset: " + offset);
        }
        byteBuffer.position(offset + mOffsetBase);
        switch (tag.getDataType()) {
            case ExifTag.TYPE_ASCII:
                byte buf[] = tag.getStringByte();
                if (buf.length == tag.getComponentCount()) {
                    buf[buf.length - 1] = 0;
                    byteBuffer.put(buf);
                } else {
                    byteBuffer.put(buf);
                    byteBuffer.put((byte) 0);
                }
                break;
            case ExifTag.TYPE_LONG:
            case ExifTag.TYPE_UNSIGNED_LONG:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    byteBuffer.putInt((int) tag.getValueAt(i));
                }
                break;
            case ExifTag.TYPE_RATIONAL:
            case ExifTag.TYPE_UNSIGNED_RATIONAL:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    Rational v = tag.getRational(i);
                    byteBuffer.putInt((int) v.getNumerator());
                    byteBuffer.putInt((int) v.getDenominator());
                }
                break;
            case ExifTag.TYPE_UNDEFINED:
            case ExifTag.TYPE_UNSIGNED_BYTE:
                buf = new byte[tag.getComponentCount()];
                tag.getBytes(buf);
                byteBuffer.put(buf);
                break;
            case ExifTag.TYPE_UNSIGNED_SHORT:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    byteBuffer.putShort((short) tag.getValueAt(i));
                }
                break;
            default:
                break;
        }
    }

    public void modifyTag(ExifTag tag) {
        mTagToModified.addTag(tag);
    }
}
