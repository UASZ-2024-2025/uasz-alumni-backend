package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.StudentCvPhotoEntity;
import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentCvPhotoRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photo du CV (ALUM-255). {@code id} est l'identifiant du profil étudiant ou celui de son compte, comme pour le CV.
 * Lecture, ajout, remplacement et suppression : l'étudiant lui-même ou un administrateur uniquement.
 * Le format est vérifié sur les premiers octets du fichier (signature JPEG ou PNG), pas sur son nom ni sur le
 * type annoncé par le navigateur.
 */
@Service
public class StudentCvPhotoService {

    private static final String ROLE_ADMIN = "ADMINISTRATEUR";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final StudentProfileRepository students;
    private final StudentCvPhotoRepository photos;
    private final CurrentUserProvider currentUserProvider;

    public StudentCvPhotoService(StudentProfileRepository students, StudentCvPhotoRepository photos,
            CurrentUserProvider currentUserProvider) {
        this.students = students;
        this.photos = photos;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public StudentCvPhotoEntity get(UUID id) {
        StudentProfileEntity student = findOwnedStudent(id);
        return photos.findById(student.getId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Cet étudiant n'a pas de photo"));
    }

    @Transactional
    public void replace(UUID id, MultipartFile file) {
        StudentProfileEntity student = findOwnedStudent(id);
        if (file == null || file.isEmpty()) {
            throw badRequest("INVALID_PHOTO_FORMAT", "Aucune image reçue : choisissez une photo JPEG ou PNG");
        }
        if (file.getSize() > StudentCvPhotoEntity.MAX_BYTES) {
            throw badRequest("PHOTO_TOO_LARGE", "La photo dépasse 2 Mo");
        }
        byte[] data = read(file);
        String contentType = detectContentType(data);
        if (contentType == null) {
            throw badRequest("INVALID_PHOTO_FORMAT", "Format non accepté : la photo doit être une image JPEG ou PNG");
        }
        StudentCvPhotoEntity photo = photos.findById(student.getId())
                .orElseGet(() -> new StudentCvPhotoEntity(student.getId()));
        photo.replace(contentType, data);
        photos.save(photo);
    }

    @Transactional
    public void delete(UUID id) {
        StudentProfileEntity student = findOwnedStudent(id);
        photos.findById(student.getId()).ifPresent(photos::delete);
    }

    /**
     * Pour {@code CvResponse.hasPhoto} : vrai si l'étudiant a une photo et que l'appelant peut la lire. Un recruteur
     * ou un Alumni qui consulte le CV voit donc {@code false} et n'essaie pas de la charger.
     */
    @Transactional(readOnly = true)
    public boolean visibleTo(StudentProfileEntity student, AuthenticatedUser user) {
        return canAccess(student, user) && photos.existsById(student.getId());
    }

    /** Type MIME d'après la signature du fichier, ou {@code null} si ce n'est ni un JPEG ni un PNG. */
    static String detectContentType(byte[] data) {
        if (startsWith(data, PNG)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (startsWith(data, JPEG)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean canAccess(StudentProfileEntity student, AuthenticatedUser user) {
        return ROLE_ADMIN.equals(user.role()) || user.id().equals(student.getUserId());
    }

    private StudentProfileEntity findOwnedStudent(UUID id) {
        StudentProfileEntity student = students.findById(id).or(() -> students.findByUserId(id)).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Profil étudiant introuvable : " + id));
        if (!canAccess(student, currentUserProvider.require())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'étudiant concerné ou un administrateur peut accéder à cette photo");
        }
        return student;
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw badRequest("INVALID_PHOTO_FORMAT", "Impossible de lire l'image envoyée");
        }
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
