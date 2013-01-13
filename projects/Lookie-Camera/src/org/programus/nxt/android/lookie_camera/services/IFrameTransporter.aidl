package org.programus.nxt.android.lookie_camera.services;

import org.programus.nxt.android.lookie_camera.video.ParcelableFrame;

interface IFrameTransporter {
	void sendFrame(in ParcelableFrame pf);
}