package com.mochat.app.mall;

import androidx.core.content.FileProvider;

/**
 * MallFileProvider — misconfigured {@link FileProvider} with {@code <root-path path=""/>}
 * (see {@code res/xml/mall_file_paths.xml}).
 *
 * <p>Used by chain #13 stage 3 (NotificationReceiver) and chain #8 (plugin plant):
 * because root-path maps to {@code /}, any caller granted a URI can read/write
 * arbitrary files. This is the canonical "theft of arbitrary files" misconfiguration
 * seen in many real-world apps.</p>
 *
 * <p>The class body is empty; all behaviour comes from the parent + the XML config.</p>
 */
public final class MallFileProvider extends FileProvider {
}
