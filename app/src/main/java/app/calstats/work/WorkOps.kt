package app.calstats.work

import java.time.LocalDate

/** Чистые операции над рабочим пространством (без Android) — легко тестировать и синхронизировать. */
object WorkOps {
    private fun now() = System.currentTimeMillis()

    // ---------- люди и группы ----------

    fun upsertPerson(w: Workspace, p: Person): Workspace =
        if (w.people.any { it.id == p.id }) w.copy(people = w.people.map { if (it.id == p.id) p else it })
        else w.copy(people = w.people + p)

    fun deletePerson(w: Workspace, id: String): Workspace = w.copy(
        people = w.people.filter { it.id != id },
        groups = w.groups.map { g -> g.copy(memberIds = g.memberIds - id) },
        issues = w.issues.map { if (it.assigneeId == id) it.copy(assigneeId = null) else it },
    )

    fun upsertGroup(w: Workspace, g: Group): Workspace =
        if (w.groups.any { it.id == g.id }) w.copy(groups = w.groups.map { if (it.id == g.id) g else it })
        else w.copy(groups = w.groups + g)

    fun deleteGroup(w: Workspace, id: String): Workspace = w.copy(
        groups = w.groups.filter { it.id != id },
        projects = w.projects.map { if (it.groupId == id) it.copy(groupId = null) else it },
    )

    // ---------- проекты ----------

    fun suggestKey(name: String): String {
        val letters = name.split(" ", "-", "_").filter { it.isNotBlank() }
        val key = if (letters.size >= 2) letters.take(3).joinToString("") { it.take(1) } else name.take(3)
        return key.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "ПР" }
    }

    fun upsertProject(w: Workspace, p: Project): Workspace =
        if (w.projects.any { it.id == p.id }) w.copy(projects = w.projects.map { if (it.id == p.id) p else it })
        else w.copy(projects = w.projects + p)

    fun deleteProject(w: Workspace, id: String): Workspace = w.copy(
        projects = w.projects.filter { it.id != id },
        issues = w.issues.filter { it.projectId != id },
        sprints = w.sprints.filter { it.projectId != id },
    )

    // ---------- задачи ----------

    fun createIssue(w: Workspace, draft: Issue): Pair<Workspace, Issue> {
        val p = w.project(draft.projectId) ?: return w to draft
        val maxOrder = w.issues.filter { it.projectId == p.id }.maxOfOrNull { it.order } ?: 0.0
        val issue = draft.copy(number = p.nextNumber, order = maxOrder + 1, created = now(), updated = now())
        val w2 = w.copy(
            issues = w.issues + issue,
            projects = w.projects.map { if (it.id == p.id) it.copy(nextNumber = it.nextNumber + 1) else it },
        )
        return w2 to issue
    }

    fun updateIssue(w: Workspace, i: Issue): Workspace {
        val old = w.issue(i.id)
        val resolved = when {
            i.status == Status.DONE && old?.status != Status.DONE -> now()
            i.status != Status.DONE -> null
            else -> i.resolvedAt
        }
        val fixed = i.copy(updated = now(), resolvedAt = resolved)
        return w.copy(issues = w.issues.map { if (it.id == i.id) fixed else it })
    }

    fun setStatus(w: Workspace, id: String, s: Status): Workspace {
        val i = w.issue(id) ?: return w
        val maxOrder = w.issues.filter { it.projectId == i.projectId && it.status == s }.maxOfOrNull { it.order } ?: 0.0
        return updateIssue(w, i.copy(status = s, order = if (i.status != s) maxOrder + 1 else i.order))
    }

    fun deleteIssue(w: Workspace, id: String): Workspace {
        val ids = setOf(id) + w.issues.filter { it.parentId == id }.map { it.id }
        return w.copy(issues = w.issues.filter { it.id !in ids }.map { if (it.epicId == id) it.copy(epicId = null) else it })
    }

    fun addWorklog(w: Workspace, issueId: String, log: WorkLog): Workspace {
        val i = w.issue(issueId) ?: return w
        return updateIssue(w, i.copy(worklogs = i.worklogs + log))
    }

    fun removeWorklog(w: Workspace, issueId: String, logId: String): Workspace {
        val i = w.issue(issueId) ?: return w
        return updateIssue(w, i.copy(worklogs = i.worklogs.filter { it.id != logId }))
    }

    fun addComment(w: Workspace, issueId: String, c: Comment): Workspace {
        val i = w.issue(issueId) ?: return w
        return updateIssue(w, i.copy(comments = i.comments + c))
    }

    // ---------- спринты ----------

    fun addSprint(w: Workspace, projectId: String): Workspace {
        val existing = w.sprints.filter { it.projectId == projectId }
        val lastEnd = existing.mapNotNull { it.endDate }.maxOrNull()
        val start = if (lastEnd != null && lastEnd.isAfter(LocalDate.now())) lastEnd.plusDays(1) else LocalDate.now()
        val n = existing.size + 1
        val s = Sprint(newId(), projectId, "Спринт $n", start = start.toString(), end = start.plusDays(13).toString())
        return w.copy(sprints = w.sprints + s)
    }

    fun updateSprint(w: Workspace, s: Sprint): Workspace = w.copy(sprints = w.sprints.map { if (it.id == s.id) s else it })

    fun startSprint(w: Workspace, id: String): Workspace {
        val s = w.sprints.firstOrNull { it.id == id } ?: return w
        val start = s.startDate ?: LocalDate.now()
        val started = s.copy(
            state = SprintState.ACTIVE,
            start = start.toString(),
            end = (s.endDate ?: start.plusDays(13)).toString(),
        )
        return w.copy(sprints = w.sprints.map { if (it.id == id) started else it })
    }

    /** Завершить спринт: незакрытые задачи — в moveTo (другой спринт) или в бэклог. */
    fun completeSprint(w: Workspace, id: String, moveTo: String?): Workspace = w.copy(
        sprints = w.sprints.map { if (it.id == id) it.copy(state = SprintState.CLOSED) else it },
        issues = w.issues.map { if (it.sprintId == id && it.status != Status.DONE) it.copy(sprintId = moveTo, updated = now()) else it },
    )

    fun deleteSprint(w: Workspace, id: String): Workspace = w.copy(
        sprints = w.sprints.filter { it.id != id },
        issues = w.issues.map { if (it.sprintId == id) it.copy(sprintId = null) else it },
    )

    fun activeSprint(w: Workspace, projectId: String?): Sprint? =
        w.sprints.firstOrNull { it.state == SprintState.ACTIVE && (projectId == null || it.projectId == projectId) }

    // ---------- расходы ----------

    fun upsertBook(w: Workspace, b: SplitBook): Workspace =
        if (w.books.any { it.id == b.id }) w.copy(books = w.books.map { if (it.id == b.id) b else it })
        else w.copy(books = w.books + b)

    fun deleteBook(w: Workspace, id: String): Workspace = w.copy(
        books = w.books.filter { it.id != id },
        expenses = w.expenses.filter { it.bookId != id },
    )

    fun upsertExpense(w: Workspace, e: Expense): Workspace =
        if (w.expenses.any { it.id == e.id }) w.copy(expenses = w.expenses.map { if (it.id == e.id) e else it })
        else w.copy(expenses = w.expenses + e)

    fun deleteExpense(w: Workspace, id: String): Workspace = w.copy(expenses = w.expenses.filter { it.id != id })

    // ---------- желания ----------

    fun upsertWishList(w: Workspace, l: WishList): Workspace =
        if (w.wishLists.any { it.id == l.id }) w.copy(wishLists = w.wishLists.map { if (it.id == l.id) l else it })
        else w.copy(wishLists = w.wishLists + l)

    fun deleteWishList(w: Workspace, id: String): Workspace = w.copy(
        wishLists = w.wishLists.filter { it.id != id },
        wishes = w.wishes.filter { it.listId != id },
    )

    fun upsertWish(w: Workspace, x: Wish): Workspace {
        val fixed = x.copy(updated = System.currentTimeMillis())
        return if (w.wishes.any { it.id == x.id }) w.copy(wishes = w.wishes.map { if (it.id == x.id) fixed else it })
        else w.copy(wishes = w.wishes + fixed)
    }

    fun deleteWish(w: Workspace, id: String): Workspace = w.copy(wishes = w.wishes.filter { it.id != id })

    // ---------- заметки ----------

    fun upsertNote(w: Workspace, n: Note): Workspace =
        if (w.notes.any { it.id == n.id }) w.copy(notes = w.notes.map { if (it.id == n.id) n else it })
        else w.copy(notes = w.notes + n)

    fun deleteNote(w: Workspace, id: String): Workspace = w.copy(notes = w.notes.filter { it.id != id })

    /** Добавляет заметки, пропуская те, что уже есть (по заголовку, тексту и дате создания). */
    fun importNotes(w: Workspace, list: List<Note>): Pair<Workspace, Int> {
        val existing = w.notes.map { Triple(it.title.trim(), it.asText().trim(), it.created / 1000) }.toHashSet()
        val fresh = list.filter { Triple(it.title.trim(), it.asText().trim(), it.created / 1000) !in existing }
        return w.copy(notes = w.notes + fresh) to fresh.size
    }
}
