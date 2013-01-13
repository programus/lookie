package org.programus.nxt.android.lookie_camera.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import android.graphics.Rect;
import android.graphics.YuvImage;

public class ImageUtilities {
	public static byte[] compressYuvImage2GzipJpeg(byte[] data, int format, int quality, int width, int height) {
		YuvImage yuvImage = new YuvImage(data, format, width, height, null);
		Rect rect = new Rect(0, 0, width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			GZIPOutputStream gzipOut = new GZIPOutputStream(out);
			yuvImage.compressToJpeg(rect, quality, gzipOut);
			gzipOut.flush();
			gzipOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] ret = out.toByteArray();
		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ret;
	}
	
	public static byte[] compressYuvImage2Jpeg(byte[] data, int format, int quality, int width, int height) {
		YuvImage yuvImage = new YuvImage(data, format, width, height, null);
		data = null;
		Rect rect = new Rect(0, 0, width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		yuvImage.compressToJpeg(rect, quality, out);
		byte[] ret = out.toByteArray();
		try {
			out.close();
			out = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ret;
	}
	
	public static byte[] compressData(byte[] data) {
		return compressDataGZIP(data);
	}
	
	public static byte[] compressDataGZIP(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			GZIPOutputStream gzipOut = new GZIPOutputStream(out);
			gzipOut.write(data);
			gzipOut.flush();
			gzipOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] ret = out.toByteArray();
		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ret;
	}

	public static byte[] scaleNV21Image(byte[] data, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
		int srcH = srcHeight >> 1;
		int srcW = srcWidth >> 1;
		int dstH = dstHeight >> 1;
		int dstW = dstWidth >> 1;
		
		byte[] dst = new byte[dstW * dstH * 6];
		int yRate = srcHeight / dstHeight;
		int yFrac = srcH % dstH;
		int xRate = srcWidth / dstWidth;
		int xFrac = srcW % dstW;
		int ye = 0;
		
		int srcOffset = srcWidth * srcHeight;
		int dstOffset = ((dstW * dstH) << 2);
		
		int srcY = 0;
		
		for (int y = 0; y < dstH; y++) {
			int dstBaseIndex4Y = dstW * 4 * y;
			int srcBaseIndex4Y = srcW * 4 * srcY;
			int dstBaseIndex4UV = dstW * 2 * y + dstOffset;
			int srcBaseIndex4UV = srcWidth * srcY + srcOffset;
			
			int srcX = 0;
			int xe = 0;
			for (int x = 0; x < dstW; x++) {
				// copy Y
				int dstIndex4Y1 = dstBaseIndex4Y + (x << 1);
				int dstIndex4Y2 = dstIndex4Y1 + (dstW << 1);
				int srcIndex4Y1 = srcBaseIndex4Y + (srcX << 1);
				int srcIndex4Y2 = srcIndex4Y1 + (srcW << 1);
				System.arraycopy(data, srcIndex4Y1, dst, dstIndex4Y1, 2);
				System.arraycopy(data, srcIndex4Y2, dst, dstIndex4Y2, 2);
				
				// copy UV
				int dstIndex4UV = dstBaseIndex4UV + (x << 1);
				int srcIndex4UV = srcBaseIndex4UV + (srcX << 1);
				System.arraycopy(data, srcIndex4UV, dst, dstIndex4UV, 2);
				
				srcX += xRate;
				xe += xFrac;
				if (xe >= dstWidth) {
					xe -= dstWidth;
					srcX++;
				}
			}
			
			srcY += yRate;
			ye += yFrac;
			if (ye >= dstHeight) {
				ye -= dstHeight;
				srcY++;
			}
		}
		
		return dst;
	}
	
}