package com.jamie.nodica.features.messages


class MessageUseCaseImpl(private val repository: MessageRepository) : MessageUseCase {
    override suspend fun getMessages(groupId: String): Result<List<Message>> = try {
        Result.success(repository.fetchMessages(groupId))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun sendMessage(message: Message): Result<Unit> = try {
        repository.sendMessage(message)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
