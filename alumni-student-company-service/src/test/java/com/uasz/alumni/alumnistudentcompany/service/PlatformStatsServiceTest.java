package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.PlatformStats;
import org.junit.jupiter.api.Test;

class PlatformStatsServiceTest {

    @Test
    void agregueLesCompteursDesRepositories() {
        AlumniRepository alumni = mock(AlumniRepository.class);
        StudentProfileRepository students = mock(StudentProfileRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        when(alumni.count()).thenReturn(120L);
        when(alumni.countEmployed()).thenReturn(96L);
        when(students.count()).thenReturn(80L);
        when(companies.countByPartnerTrue()).thenReturn(25L);

        PlatformStats stats = new PlatformStatsService(alumni, students, companies).getStats();

        assertEquals(120L, stats.getAlumniCount());
        assertEquals(96L, stats.getEmployedAlumniCount());
        assertEquals(80L, stats.getStudentCount());
        assertEquals(25L, stats.getCompanyCount());
    }
}
