package com.lifepowers.app.data

import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: BehaviorEventDao,
) : EventRepository {

    override suspend fun add(event: BehaviorEvent): Long = dao.insert(event.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override fun between(fromUtcMs: Long, toUtcMs: Long): Flow<List<BehaviorEvent>> =
        dao.between(fromUtcMs, toUtcMs).map { rows -> rows.map { it.toDomain() } }

    override suspend fun countOfKind(kind: String): Int = dao.countOfKind(kind)
}

fun BehaviorEvent.toEntity() = BehaviorEventEntity(
    id = id,
    kind = kind,
    startUtc = startUtcMs,
    endUtc = endUtcMs,
    zoneOffsetMin = zoneOffsetMin,
    quantity = quantity,
    unit = unit,
    note = note,
    source = source,
)

fun BehaviorEventEntity.toDomain() = BehaviorEvent(
    id = id,
    kind = kind,
    startUtcMs = startUtc,
    endUtcMs = endUtc,
    zoneOffsetMin = zoneOffsetMin,
    quantity = quantity,
    unit = unit,
    note = note,
    source = source,
)
