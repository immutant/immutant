/*
 * Copyright 2008-2014 Red Hat, Inc, and individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.immutant.web;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class ReusableInputStream extends InputStream {

    public ReusableInputStream(InputStream src) {
        this.source = src;
    }

    private RandomAccessFile data() throws IOException {
        if (this.data == null) {
            File tmpDir = new File(System.getProperty("jboss.server.temp.dir"));
            this.tmpFile = File.createTempFile("ImmutantReusableInputStream", null, tmpDir);
            this.data = new RandomAccessFile(this.tmpFile, "rw");

            int bytesRead;
            int transferPosition = 0;
            byte[] bytes = new byte[4096];
            while ((bytesRead = this.source.read(bytes, transferPosition, 4096)) > 0) {
                this.data.write(bytes, transferPosition, bytesRead);
                transferPosition += bytesRead;
            }
            this.data.seek(0);
            this.source.close();
        }

        return this.data;
    }

    @Override
    public int read() throws IOException {
        return data().read();
    }

    @Override
    public void close() throws IOException {
        //only reset if we actually read from the source stream
         if (this.data != null) {
            this.data.seek(0);
        }
    }

    public void hardClose() throws IOException {
        source.close();
        if (tmpFile != null) {
            data.close();
            tmpFile.delete();
        }
    }

    public Closeable closer() {
        return new Closeable() {
            public void close() throws IOException {
                hardClose();
            }
        };
    }

    private InputStream source;
    private File tmpFile;
    private RandomAccessFile data;

}
