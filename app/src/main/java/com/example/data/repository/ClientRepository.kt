package com.example.data.repository

import com.example.data.local.dao.ClientDao
import com.example.data.local.entity.Client
import kotlinx.coroutines.flow.Flow

interface ClientRepository {
    fun getActiveClients(): Flow<List<Client>>
    fun getAllClients(): Flow<List<Client>>
    fun getClientById(id: Long): Flow<Client?>
    suspend fun insertClient(client: Client): Long
    suspend fun updateClient(client: Client)
    suspend fun deleteClient(client: Client)
}

class ClientRepositoryImpl(private val clientDao: ClientDao) : ClientRepository {
    override fun getActiveClients(): Flow<List<Client>> = clientDao.getActiveClients()
    override fun getAllClients(): Flow<List<Client>> = clientDao.getAllClients()
    override fun getClientById(id: Long): Flow<Client?> = clientDao.getClientById(id)
    override suspend fun insertClient(client: Client): Long = clientDao.insertClient(client)
    override suspend fun updateClient(client: Client) = clientDao.updateClient(client)
    override suspend fun deleteClient(client: Client) = clientDao.deleteClient(client)
}
