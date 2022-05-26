package com.github.hhhzzzsss.epsilonbot.mapart;

import com.github.hhhzzzsss.epsilonbot.util.DownloadUtils;
import com.github.hhhzzzsss.epsilonbot.util.MapUtils;
import lombok.Getter;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class MapartLoaderThread extends Thread {
	private String strUrl;
	private URL url;
	@Getter private int horizDim;
	@Getter private int vertDim;
	@Getter private Throwable exception;
	@Getter private BlockElevation[][] blocks;
	@Getter private int maxElevation = 0;
	
	public MapartLoaderThread(String strUrl, int horizDim, int vertDim) throws IOException {
		this.strUrl = strUrl;
		this.url = new URL(strUrl);
		if (!this.url.getProtocol().startsWith("http")) {
			throw new IOException("Illegal protocol: must use http or https");
		}
		this.horizDim = horizDim;
		this.vertDim = vertDim;
		this.blocks = new BlockElevation[128*horizDim*vertDim][129];

		setDefaultUncaughtExceptionHandler((t, e) -> {
			exception = e;
		});
	}
	
	public void run() {
		BufferedImage img;
		BufferedImage imgTransform;
		try {
			img = ImageIO.read(DownloadUtils.DownloadToOutputStream(url, 50*1024*1024));
		} catch (Exception e) {
			exception = e;
			return;
		}

		if (img == null) {
			exception = new IOException("Error: failed to load as image");
			return;
		}
		
		// Crop to make square
		double cropSize = Math.min((double)img.getWidth() / horizDim, (double)img.getHeight() / vertDim);
		imgTransform = Scalr.crop(
				img,
				(int) Math.round((img.getWidth()-cropSize*horizDim)/2.0),
				(int) Math.round((img.getHeight()-cropSize*vertDim)/2.0),
				(int) Math.round(cropSize*horizDim),
				(int) Math.round(cropSize*vertDim)
		);
		img.flush();
		img = imgTransform;
		
		// Resize to 128x128
		imgTransform = Scalr.resize(img, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 128*horizDim, 128*vertDim);
		img.flush();
		img = imgTransform;
		
		// Fill transparency with white
		imgTransform = new BufferedImage(128*horizDim, 128*vertDim, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = imgTransform.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, 128*horizDim, 128*vertDim);
		g2d.drawImage(img, 0, 0, null);
		g2d.dispose();
		img.flush();
		img = imgTransform;
		
		System.out.println(String.format("Resulting image size: %dpx x %dpx", img.getWidth(), img.getHeight()));
		
		// Noobline
		for (int x = 0; x < 128*horizDim*vertDim; x++) {
			blocks[x][0] = new BlockElevation("stone", 1, Color.BLACK); // The tone and color of the noobline doesn't matter
		}

		loadBlocksWithDithering(img);
		
		// Calcualate elevations
		/*
		 *tone=0 means the block is placed under the block north of it
		 *tone=1 means the block is placed at the same level as the block north of it
		 *tone=2 means the block is placed above the block north of it
		 */
		for (int x = 0; x < 128*horizDim*vertDim; x++) {
			for (int z = 0; z < 128; z++) {
				int tone = blocks[x][z + 1].tone;
				if (tone == 0) {
					blocks[x][z + 1].elevation = blocks[x][z].elevation - 1;
				} else if (tone == 1) {
					blocks[x][z + 1].elevation = blocks[x][z].elevation;
				} else {
					blocks[x][z + 1].elevation = blocks[x][z].elevation + 1;
				}
			}
			int minElevation = 0;
			for (int z = 0; z < 129; z++) {
				if (blocks[x][z].elevation < minElevation) {
					minElevation = blocks[x][z].elevation;
				}
			}
			for (int z = 0; z < 129; z++) {
				blocks[x][z].elevation -= minElevation;
			}
			for (int z = 0; z < 129; z++) {
				if (blocks[x][z].elevation > maxElevation) {
					maxElevation = blocks[x][z].elevation;
				}
			}
		}
	}

	private void loadBlocksWithDithering(BufferedImage img) {
		// a bit of extra padding at the bottom so that I don't have to use as many if statements during the loop
		double[][][] ditherCache = new double[128*horizDim][128*vertDim+1][3];
		for (int y=0; y<128*vertDim; y++) {
			for (int x=0; x<128*horizDim; x++) {
				int rgb = img.getRGB(x, y);
				int r = (rgb & 0x00ff0000) >> 16;
				int g = (rgb & 0x0000ff00) >> 8;
				int b = (rgb & 0x000000ff);
				ditherCache[x][y][0] = r;
				ditherCache[x][y][1] = g;
				ditherCache[x][y][2] = b;
			}
		}

		int direction = 1;
		for (int y=0; y<128*vertDim; y++) {
			if (direction == 1) {
				for (int x = 0;  x < 128*horizDim; x++) {
					int bx = x + (y/128)*(128*horizDim);
					int bz = y%128+1;
					BlockElevation be = getNearestMapColor(ditherCache[x][y][0], ditherCache[x][y][1],ditherCache[x][y][2]);
					blocks[bx][bz] = be;
					double errorR = ditherCache[x][y][0] - be.color.getRed();
					double errorG = ditherCache[x][y][1] - be.color.getGreen();
					double errorB = ditherCache[x][y][2] - be.color.getBlue();
					if (x>0) {
						ditherCache[x-1][y+1][0] += (3./16.) * errorR;
						ditherCache[x-1][y+1][1] += (3./16.) * errorG;
						ditherCache[x-1][y+1][2] += (3./16.) * errorB;
					}
					if (x<128*horizDim-1) {
						ditherCache[x+1][y][0] += (7./16.) * errorR;
						ditherCache[x+1][y][1] += (7./16.) * errorG;
						ditherCache[x+1][y][2] += (7./16.) * errorB;
						ditherCache[x+1][y+1][0] += (1./16.) * errorR;
						ditherCache[x+1][y+1][1] += (1./16.) * errorG;
						ditherCache[x+1][y+1][2] += (1./16.) * errorB;
					}
					ditherCache[x][y+1][0] += (5./16.) * errorR;
					ditherCache[x][y+1][1] += (5./16.) * errorG;
					ditherCache[x][y+1][2] += (5./16.) * errorB;
				}
			} else {
				for (int x = 128*horizDim-1;  x >= 0; x--) {
					int bx = x + (y/128)*(128*horizDim);
					int bz = y%128+1;
					BlockElevation be = getNearestMapColor(ditherCache[x][y][0], ditherCache[x][y][1],ditherCache[x][y][2]);
					blocks[bx][bz] = be;
					double errorR = ditherCache[x][y][0] - be.color.getRed();
					double errorG = ditherCache[x][y][1] - be.color.getGreen();
					double errorB = ditherCache[x][y][2] - be.color.getBlue();
					if (x<128*horizDim-1) {
						ditherCache[x+1][y+1][0] += (3./16.) * errorR;
						ditherCache[x+1][y+1][1] += (3./16.) * errorG;
						ditherCache[x+1][y+1][2] += (3./16.) * errorB;
					}
					if (x>0) {
						ditherCache[x-1][y][0] += (7./16.) * errorR;
						ditherCache[x-1][y][1] += (7./16.) * errorG;
						ditherCache[x-1][y][2] += (7./16.) * errorB;
						ditherCache[x-1][y+1][0] += (1./16.) * errorR;
						ditherCache[x-1][y+1][1] += (1./16.) * errorG;
						ditherCache[x-1][y+1][2] += (1./16.) * errorB;
					}
					ditherCache[x][y+1][0] += (5./16.) * errorR;
					ditherCache[x][y+1][1] += (5./16.) * errorG;
					ditherCache[x][y+1][2] += (5./16.) * errorB;
				}
			}
			direction = -direction;
		}
		MapartManager.getMapartIndex().add(new MapartManager.MapartInfo(strUrl, horizDim, vertDim));
		try {
			MapartManager.saveMapartIndex();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static BlockElevation getNearestMapColor(double r, double g, double b) {
		BlockElevation nearest = null;
		double minDistSq = Integer.MAX_VALUE;
		for (MapUtils.MapColor mc : MapUtils.getColors()) for (int tone = 0; tone<3; tone++) {
			Color c = mc.getColors()[tone];
			double dr = r - c.getRed();
			double dg = g - c.getGreen();
			double db = b - c.getBlue();
			double distSq = dr*dr + dg*dg + db*db;
			if (distSq < minDistSq) {
				nearest = new BlockElevation(mc.getBlock(), tone, c);
				minDistSq = distSq;
			}
		}
		
		return nearest;
	}
}
