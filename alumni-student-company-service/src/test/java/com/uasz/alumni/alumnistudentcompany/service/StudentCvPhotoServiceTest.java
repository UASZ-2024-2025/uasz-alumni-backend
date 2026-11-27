package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ALUM-255 : reconnaissance du format d'après les premiers octets du fichier. */
class StudentCvPhotoServiceTest {

    @Test
    void recognisesPngAndJpegSignatures() {
        assertThat(StudentCvPhotoService.detectContentType(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).isEqualTo("image/png");
        assertThat(StudentCvPhotoService.detectContentType(
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB})).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsOtherFormatsAndTruncatedFiles() {
        assertThat(StudentCvPhotoService.detectContentType("GIF89a".getBytes())).isNull();
        assertThat(StudentCvPhotoService.detectContentType("%PDF-1.7".getBytes())).isNull();
        assertThat(StudentCvPhotoService.detectContentType(new byte[] {(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(StudentCvPhotoService.detectContentType(new byte[0])).isNull();
    }
}
