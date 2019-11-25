package org.builder.session.jackson.utils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Preconditions;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.NONE)
public class FileUtilities {

    public static File createTempFile(@NonNull final Optional<String> baseName) {
        String name = baseName.orElse("TempFile");
        String timestamp = Long.toString(System.currentTimeMillis());
        String randomId = Integer.toString(ThreadLocalRandom.current().nextInt());
        String fileName = String.join("_", name, timestamp, randomId);

        try {
            File file = File.createTempFile(fileName, ".tmp");
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temporary file " + fileName, e);
        }
    }

    public static void reset(@NonNull final File file) {
        delete(file);
        create(file);
        setReadPermissions(file, true);
        setWritePermissions(file, true);
    }

    public static void create(@NonNull final File file) {
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create file " + toString(file), e);
        }
    }

    public static void delete(@NonNull final File file) {
        if(file.exists()) {
            boolean successfullyDeleted = file.delete();
            if(!successfullyDeleted) {
                throw new IllegalStateException("Failed to delete file " + toString(file));
            }
        }
    }

    public static void setReadPermissions(@NonNull final File file, final boolean newValue) {
        Preconditions.checkArgument(file.exists(), "The file " + toString(file) + " must exist to set permissions.");
        if(!file.setReadable(newValue)) {
            throw new IllegalStateException("Failed to set read permissions for " + toString(file));
        }
    }


    public static void setWritePermissions(@NonNull final File file, final boolean newValue) {
        Preconditions.checkArgument(file.exists(), "The file " + toString(file) + " must exist to set permissions.");
        if(!file.setWritable(newValue)) {
            throw new IllegalStateException("Failed to set write permissions for " + toString(file));
        }
    }

    public static String toString(@NonNull File file) {
        return file.getName()  + ". ( "+ file.getAbsolutePath() + " )";
    }
}
