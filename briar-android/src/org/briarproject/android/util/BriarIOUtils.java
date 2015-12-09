package org.briarproject.android.util;

import java.io.File;

/**
 * Created by Ernir Erlingsson (ernir@ymirmobile.com) on 9.12.2015.
 */
public class BriarIOUtils {

    public static void deleteFileOrDir(File f)
    {
        if (f.isFile())
            f.delete();
        else if (f.isDirectory())
            for (File child : f.listFiles())
                deleteFileOrDir(child);
    }
}
