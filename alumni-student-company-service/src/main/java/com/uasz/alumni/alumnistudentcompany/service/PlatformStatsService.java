package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.PlatformStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agrège les compteurs de la plateforme pour le tableau de bord administrateur (ALUM-182). */
@Service
public class PlatformStatsService {

    private final AlumniRepository alumni;
    private final StudentProfileRepository students;
    private final CompanyRepository companies;

    public PlatformStatsService(AlumniRepository alumni,
                                StudentProfileRepository students,
                                CompanyRepository companies) {
        this.alumni = alumni;
        this.students = students;
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public PlatformStats getStats() {
        return new PlatformStats()
                .alumniCount(alumni.count())
                .employedAlumniCount(alumni.countEmployed())
                .studentCount(students.count())
                .companyCount(companies.countByPartnerTrue());
    }
}
