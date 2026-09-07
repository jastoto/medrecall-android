package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.NoteDao
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val doctorDao: DoctorDao
) {
    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    suspend fun save(note: Note): Long = noteDao.insert(note)
}
