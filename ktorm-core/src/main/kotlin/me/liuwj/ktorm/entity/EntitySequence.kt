package me.liuwj.ktorm.entity

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.expression.OrderByExpression
import me.liuwj.ktorm.expression.SelectExpression
import me.liuwj.ktorm.schema.Column
import me.liuwj.ktorm.schema.ColumnDeclaring
import me.liuwj.ktorm.schema.Table
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

data class EntitySequence<E : Entity<E>, T : Table<E>>(val sourceTable: T, val expression: SelectExpression) {

    val query = Query(expression)

    val sql get() = query.sql

    val rowSet get() = query.rowSet

    val totalRecords get() = query.totalRecords

    fun asKotlinSequence() = Sequence { iterator() }

    operator fun iterator() = object : Iterator<E> {
        private val queryIterator = query.iterator()

        override fun hasNext(): Boolean {
            return queryIterator.hasNext()
        }

        override fun next(): E {
            return sourceTable.createEntity(queryIterator.next())
        }
    }
}

fun <E : Entity<E>, T : Table<E>> T.asSequence(): EntitySequence<E, T> {
    val query = this.joinReferencesAndSelect()
    return EntitySequence(this, query.expression as SelectExpression)
}

fun <E : Entity<E>, C : MutableCollection<in E>> EntitySequence<E, *>.toCollection(destination: C): C {
    return asKotlinSequence().toCollection(destination)
}

fun <E : Entity<E>> EntitySequence<E, *>.toList(): List<E> {
    return asKotlinSequence().toList()
}

fun <E : Entity<E>> EntitySequence<E, *>.toMutableList(): MutableList<E> {
    return asKotlinSequence().toMutableList()
}

fun <E : Entity<E>> EntitySequence<E, *>.toSet(): Set<E> {
    return asKotlinSequence().toSet()
}

fun <E : Entity<E>> EntitySequence<E, *>.toMutableSet(): MutableSet<E> {
    return asKotlinSequence().toMutableSet()
}

fun <E : Entity<E>> EntitySequence<E, *>.toHashSet(): HashSet<E> {
    return asKotlinSequence().toHashSet()
}

fun <E> EntitySequence<E, *>.toSortedSet(): SortedSet<E> where E : Entity<E>, E : Comparable<E> {
    return asKotlinSequence().toSortedSet()
}

fun <E> EntitySequence<E, *>.toSortedSet(
    comparator: Comparator<in E>
): SortedSet<E> where E : Entity<E>, E : Comparable<E> {
    return asKotlinSequence().toSortedSet(comparator)
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.filterColumns(
    selector: (T) -> List<Column<*>>
): EntitySequence<E, T> {
    val columns = selector(sourceTable)
    if (columns.isEmpty()) {
        return this
    } else {
        return this.copy(expression = expression.copy(columns = columns.map { it.asDeclaringExpression() }))
    }
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.filter(
    predicate: (T) -> ColumnDeclaring<Boolean>
): EntitySequence<E, T> {
    if (expression.where == null) {
        return this.copy(expression = expression.copy(where = predicate(sourceTable).asExpression()))
    } else {
        return this.copy(expression = expression.copy(where = expression.where and predicate(sourceTable)))
    }
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.filterNot(
    predicate: (T) -> ColumnDeclaring<Boolean>
): EntitySequence<E, T> {
    return this.filter { !predicate(it) }
}

inline fun <E : Entity<E>, T : Table<E>, C : MutableCollection<in E>> EntitySequence<E, T>.filterTo(
    destination: C,
    predicate: (T) -> ColumnDeclaring<Boolean>
): C {
    return this.filter(predicate).toCollection(destination)
}

inline fun <E : Entity<E>, T : Table<E>, C : MutableCollection<in E>> EntitySequence<E, T>.filterNotTo(
    destination: C,
    predicate: (T) -> ColumnDeclaring<Boolean>
): C {
    return this.filterNot(predicate).toCollection(destination)
}

inline fun <E : Entity<E>, R> EntitySequence<E, *>.map(transform: (E) -> R): List<R> {
    return this.mapTo(ArrayList(), transform)
}

inline fun <E : Entity<E>, R, C : MutableCollection<in R>> EntitySequence<E, *>.mapTo(
    destination: C,
    transform: (E) -> R
): C {
    for (item in this) destination += transform(item)
    return destination
}

inline fun <E : Entity<E>, R> EntitySequence<E, *>.mapIndexed(transform: (index: Int, E) -> R): List<R> {
    return this.mapIndexedTo(ArrayList(), transform)
}

inline fun <E : Entity<E>, R, C : MutableCollection<in R>> EntitySequence<E, *>.mapIndexedTo(
    destination: C,
    transform: (index: Int, E) -> R
): C {
    var index = 0
    return this.mapTo(destination) { transform(index++, it) }
}

inline fun <E : Entity<E>, T : Table<E>, C : Any> EntitySequence<E, T>.aggregate(
    aggregationSelector: (T) -> ColumnDeclaring<C>
): C? {
    val aggregation = aggregationSelector(sourceTable)

    val expr = expression.copy(
        columns = listOf(aggregation.asDeclaringExpression())
    )

    val rowSet = Query(expr).rowSet

    if (rowSet.size() == 1) {
        assert(rowSet.next())
        return aggregation.sqlType.getResult(rowSet, 1)
    } else {
        val (sql, _) = Database.global.formatExpression(expr, beautifySql = true)
        throw IllegalStateException("Expected 1 result but ${rowSet.size()} returned from sql: \n\n$sql")
    }
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.count(): Int {
    return aggregate { me.liuwj.ktorm.dsl.count() } ?: error("Count expression returns null, which never happens.")
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.count(predicate: (T) -> ColumnDeclaring<Boolean>): Int {
    return this.filter(predicate).count()
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.none(): Boolean {
    return this.count() == 0
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.none(predicate: (T) -> ColumnDeclaring<Boolean>): Boolean {
    return this.count(predicate) == 0
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.any(): Boolean {
    return this.count() > 0
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.any(predicate: (T) -> ColumnDeclaring<Boolean>): Boolean {
    return this.count(predicate) > 0
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.all(predicate: (T) -> ColumnDeclaring<Boolean>): Boolean {
    return this.none { !predicate(it) }
}

inline fun <E : Entity<E>, T : Table<E>, C : Number> EntitySequence<E, T>.sumBy(selector: (T) -> ColumnDeclaring<C>): C? {
    return aggregate { sum(selector(it)) }
}

inline fun <E : Entity<E>, T : Table<E>, C : Number> EntitySequence<E, T>.maxBy(selector: (T) -> ColumnDeclaring<C>): C? {
    return aggregate { max(selector(it)) }
}

inline fun <E : Entity<E>, T : Table<E>, C : Number> EntitySequence<E, T>.minBy(selector: (T) -> ColumnDeclaring<C>): C? {
    return aggregate { min(selector(it)) }
}

inline fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.averageBy(selector: (T) -> ColumnDeclaring<out Number>): Double? {
    return aggregate { avg(selector(it)) }
}

fun <E : Entity<E>, K, V> EntitySequence<E, *>.associate(transform: (E) -> Pair<K, V>): Map<K, V> {
    return this.associateTo(LinkedHashMap(), transform)
}

fun <E : Entity<E>, K> EntitySequence<E, *>.associateBy(keySelector: (E) -> K): Map<K, E> {
    return this.associateByTo(LinkedHashMap(), keySelector)
}

fun <E : Entity<E>, K, V> EntitySequence<E, *>.associateBy(keySelector: (E) -> K, valueTransform: (E) -> V): Map<K, V> {
    return this.associateByTo(LinkedHashMap(), keySelector, valueTransform)
}

fun <K : Entity<K>, V> EntitySequence<K, *>.associateWith(valueTransform: (K) -> V): Map<K, V> {
    return this.associateWithTo(LinkedHashMap(), valueTransform)
}

fun <E : Entity<E>, K, V, M : MutableMap<in K, in V>> EntitySequence<E, *>.associateTo(destination: M, transform: (E) -> Pair<K, V>): M {
    for (item in this) {
        destination += transform(item)
    }
    return destination
}

fun <E : Entity<E>, K, M : MutableMap<in K, in E>> EntitySequence<E, *>.associateByTo(destination: M, keySelector: (E) -> K): M {
    for (item in this) {
        destination.put(keySelector(item), item)
    }
    return destination
}

fun <E : Entity<E>, K, V, M : MutableMap<in K, in V>> EntitySequence<E, *>.associateByTo(destination: M, keySelector: (E) -> K, valueTransform: (E) -> V): M {
    for (item in this) {
        destination.put(keySelector(item), valueTransform(item))
    }
    return destination
}

fun <K : Entity<K>, V, M : MutableMap<in K, in V>> EntitySequence<K, *>.associateWithTo(destination: M, valueTransform: (K) -> V): M {
    for (item in this) {
        destination.put(item, valueTransform(item))
    }
    return destination
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.drop(n: Int): EntitySequence<E, T> {
    if (n == 0) {
        return this
    } else {
        val offset = expression.offset ?: 0
        return this.copy(expression = expression.copy(offset = offset + n))
    }
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.take(n: Int): EntitySequence<E, T> {
    val limit = expression.limit ?: Int.MAX_VALUE
    return this.copy(expression = expression.copy(limit = min(limit, n)))
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.elementAtOrNull(index: Int): E? {
    try {
        val iterator = this.drop(index).take(1).iterator()
        if (iterator.hasNext()) {
            return iterator.next()
        } else {
            return null
        }

    } catch (e: UnsupportedOperationException) {

        val iterator = this.iterator()
        var count = 0
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (index == count++) {
                return item
            }
        }
        return null
    }
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.elementAtOrElse(index: Int, defaultValue: (Int) -> E): E {
    return this.elementAtOrNull(index) ?: defaultValue(index)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.elementAt(index: Int): E {
    return this.elementAtOrNull(index) ?: throw IndexOutOfBoundsException("Sequence doesn't contain element at index $index.")
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.firstOrNull(): E? {
    return this.elementAtOrNull(0)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.firstOrNull(predicate: (T) -> ColumnDeclaring<Boolean>): E? {
    return this.filter(predicate).elementAtOrNull(0)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.first(): E {
    return this.elementAt(0)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.first(predicate: (T) -> ColumnDeclaring<Boolean>): E {
    return this.filter(predicate).elementAt(0)
}

fun <E : Entity<E>> EntitySequence<E, *>.lastOrNull(): E? {
    var last: E? = null
    for (item in this) {
        last = item
    }
    return last
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.lastOrNull(predicate: (T) -> ColumnDeclaring<Boolean>): E? {
    return this.filter(predicate).lastOrNull()
}

fun <E : Entity<E>> EntitySequence<E, *>.last(): E {
    return lastOrNull() ?: throw NoSuchElementException("Sequence is empty.")
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.last(predicate: (T) -> ColumnDeclaring<Boolean>): E {
    return this.filter(predicate).last()
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.find(predicate: (T) -> ColumnDeclaring<Boolean>): E? {
    return this.firstOrNull(predicate)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.findLast(predicate: (T) -> ColumnDeclaring<Boolean>): E? {
    return this.lastOrNull(predicate)
}

fun <E : Entity<E>, R> EntitySequence<E, *>.fold(initial: R, operation: (acc: R, E) -> R): R {
    var accumulator = initial
    for (item in this) {
        accumulator = operation(accumulator, item)
    }
    return accumulator
}

fun <E : Entity<E>, R> EntitySequence<E, *>.foldIndexed(initial: R, operation: (index: Int, acc: R, E) -> R): R {
    var index = 0
    return this.fold(initial) { acc, e -> operation(index++, acc, e) }
}

fun <E : Entity<E>> EntitySequence<E, *>.forEach(action: (E) -> Unit) {
    for (item in this) action(item)
}

fun <E : Entity<E>> EntitySequence<E, *>.forEachIndexed(action: (index: Int, E) -> Unit) {
    var index = 0
    for (item in this) action(index++, item)
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.sorted(selector: (T) -> List<OrderByExpression>): EntitySequence<E, T> {
    return this.copy(expression = expression.copy(orderBy = selector(sourceTable)))
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.sortedBy(selector: (T) -> ColumnDeclaring<*>): EntitySequence<E, T> {
    return this.sorted { listOf(selector(it).asc()) }
}

fun <E : Entity<E>, T : Table<E>> EntitySequence<E, T>.sortedByDescending(selector: (T) -> ColumnDeclaring<*>): EntitySequence<E, T> {
    return this.sorted { listOf(selector(it).desc()) }
}

fun <E : Entity<E>, T : Table<E>, K : Any> EntitySequence<E, T>.groupingBy(keySelector: (T) -> ColumnDeclaring<K>): EntityGrouping<E, T, K> {
    return EntityGrouping(this, keySelector)
}