/*
 * Utility to embed data into images
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) 2007 Samir Vaidya
 */

package net.sourceforge.openstego;

import net.sourceforge.openstego.util.LabelUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream to embed data into image
 */
public class StegoOutputStream extends OutputStream
{
    /**
     * Image data
     */
    private BufferedImage image = null;

    /**
     * Number of bits used per color channel
     */
    private int channelBitsUsed = 1;

    /**
     * Length of the data
     */
    private int dataLength = 0;

    /**
     * Current x co-ordinate
     */
    private int x = 0;

    /**
     * Current y co-ordinate
     */
    private int y = 0;

    /**
     * Current bit number to be read
     */
    private int currBit = 0;

    /**
     * Bit set to store three bits per pixel
     */
    private byte[] bitSet = null;

    /**
     * Width of the image
     */
    private int imgWidth = 0;

    /**
     * Height of the image
     */
    private int imgHeight = 0;

    /**
     * Configuration data
     */
    private StegoConfig config = null;

    /**
     * Default constructor
     * @param image Source image into which data will be embedded
     * @param dataLength Length of the data that would be written to the image
     * @param config Configuration data to use while writing
     * @throws IOException
     */
    public StegoOutputStream(BufferedImage image, int dataLength, StegoConfig config) throws IOException
    {
        if(image == null)
        {
            throw new IllegalArgumentException(LabelUtil.getString("err.image.arg.nullValue"));
        }

        if(image.getColorModel() instanceof java.awt.image.IndexColorModel)
        {
            throw new IllegalArgumentException(LabelUtil.getString("err.image.indexed"));
        }

        this.image = image;
        this.dataLength = dataLength;
        this.imgWidth = image.getWidth();
        this.imgHeight = image.getHeight();
        this.config = config;

        this.channelBitsUsed = 1;
        this.bitSet = new byte[3];
        writeHeader();
    }

    /**
     * Method to write header data to stream
     * @throws IOException
     */
    private void writeHeader() throws IOException
    {
        int channelBits = 1;
        int noOfPixels = 0;

        noOfPixels = imgWidth * imgHeight;
        while(true)
        {
            if((noOfPixels * channelBits) / 8 < (dataLength + DataHeader.getHeaderSize()))
            {
                channelBits++;
                if(channelBits > config.getMaxBitsUsedPerChannel())
                {
                    throw new IOException(LabelUtil.getString("err.image.insufficientSize"));
                }
            }
            else
            {
                break;
            }
        }

        // Write header with channelBitsUsed = 1
        write((new DataHeader(dataLength, channelBits, config)).getHeaderData());
        if(currBit != 0)
        {
            currBit = 0;
            writeCurrentBitSet();
            nextPixel();
        }

        this.channelBitsUsed = channelBits;
        this.bitSet = new byte[3 * channelBits];
    }

    /**
     * Implementation of <code>OutputStream.write(int)</code> method
     * @param data Byte to be written
     * @throws IOException
     */
    public void write(int data) throws IOException
    {
        for(int bit = 0; bit < 8; bit++)
        {
            bitSet[currBit] = (byte) ((data >> (7 - bit)) & 1);
            currBit++;
            if(currBit == bitSet.length)
            {
                currBit = 0;
                writeCurrentBitSet();
                nextPixel();
            }
        }
    }

    /**
     * Flushes the stream
     * @throws IOException
     */
    public void flush() throws IOException
    {
        writeCurrentBitSet();
    }

    /**
     * Closes the stream
     * @throws IOException
     */
    public void close() throws IOException
    {
        if(currBit != 0)
        {
            for(int i = currBit; i < bitSet.length; i++)
            {
                bitSet[i] = 0;
            }
            currBit = 0;
            writeCurrentBitSet();
            nextPixel();
        }
        super.close();
    }

    /**
     * Get the image containing the embedded data. Ideally, this should be called after the stream is closed.
     * @return Image data
     * @throws IOException
     */
    public BufferedImage getImage() throws IOException
    {
        flush();
        return image;
    }

    /**
     * Get method for dataLength
     * @return dataLength
     */
    public int getDataLength()
    {
        return dataLength;
    }

    /**
     * Get method for channelBitsUsed
     * @return channelBitsUsed
     */
    public int getChannelBitsUsed()
    {
        return channelBitsUsed;
    }

    /**
     * Method to write current bit set
     * @throws IOException
     */
    private void writeCurrentBitSet() throws IOException
    {
        int pixel = 0;
        int offset = 0;
        int mask = 0;
        int maskPerByte = 0;
        int bitOffset = 0;

        if(y == imgHeight)
        {
            throw new IOException(LabelUtil.getString("err.image.insufficientSize"));
        }

        maskPerByte = (int) (Math.pow(2, channelBitsUsed) - 1);
        mask = (maskPerByte << 16) + (maskPerByte << 8) + maskPerByte;
        pixel = image.getRGB(x, y) & (0xFFFFFFFF - mask);

        for(int bit = 0; bit < 3; bit++)
        {
            bitOffset = 0;
            for(int i = 0; i < channelBitsUsed; i++)
            {
                bitOffset = (bitOffset << 1) + bitSet[(bit * channelBitsUsed) + i];
            }
            offset = (offset << 8) + bitOffset;
        }
        image.setRGB(x, y, pixel + offset);
    }

    /**
     * Method to move on to next pixel
     */
    private void nextPixel()
    {
        x++;
        if(x == imgWidth)
        {
            x = 0;
            y++;
        }
    }
}