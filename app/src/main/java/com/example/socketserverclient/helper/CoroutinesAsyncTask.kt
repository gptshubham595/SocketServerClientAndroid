package com.example.socketserverclient.helper

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class CoroutinesAsyncTask<Params, Progress, Result> {
    enum class Status {
        PENDING,
        RUNNING,
        FINISHED,
    }

    private val _status = AtomicReference<Status>(Status.PENDING)
    val status get() = _status.get()!!
    private val _isCancelled = AtomicBoolean(false)
    val isCancelled get() = _isCancelled.get()
    private val coroutineJob = AtomicReference<Job?>(null)
    private var executionResult = AtomicReference<Result?>()
    private var executionException = AtomicReference<Throwable?>()
    private val lock = ReentrantLock(true)
    private val executionCompletedCondition = lock.newCondition()

    @WorkerThread
    protected abstract fun doInBackground(vararg params: Params): Result

    @MainThread
    protected open fun onPreExecute() {
    }

    @MainThread
    protected open fun onPostExecute(result: Result?) {
    }

    @MainThread
    protected open fun onProgressUpdate(vararg values: Progress) {
    }

    @MainThread
    protected open fun onCancelled(result: Result?) {
        onCancelled()
    }

    @MainThread
    protected open fun onCancelled() {
    }

    @MainThread
    fun execute(vararg params: Params): CoroutinesAsyncTask<Params, Progress, Result> {
        when (status) {
            Status.RUNNING -> throw IllegalStateException(
                "Cannot execute task: the task is already running."
            )

            Status.FINISHED -> throw IllegalStateException(
                "Cannot execute task: the task has already been executed (a task can be executed only once)"
            )

            Status.PENDING -> {}
        }
        _status.set(Status.RUNNING)
        onPreExecute()
        val job = COROUTINE_SCOPE.launch(BACKGROUND_DISPATCHER) {
            try {
                if (!isCancelled) {
                    runInterruptible {
                        executionResult.set(doInBackground(*params))
                    }
                }
                if (isCancelled) {
                    throw CancellationException()
                }
            } catch (t: Throwable) {
                _isCancelled.set(true)
                when (t) {
                    is CancellationException -> executionException.set(java.util.concurrent.CancellationException())
                    else -> executionException.set(ExecutionException(t))
                }
            } finally {
                finish()
                _status.set(Status.FINISHED)
                lock.withLock {
                    executionCompletedCondition.signalAll()
                }
            }
        }
        coroutineJob.set(job)
        return this
    }

    private fun finish() = COROUTINE_SCOPE.launch(UI_DISPATCHER) {
        val result = executionResult.get()
        if (isCancelled) {
            onCancelled(result)
        } else {
            onPostExecute(result)
        }
    }

    @WorkerThread
    fun publishProgress(vararg values: Progress) {
        if (!isCancelled) {
            COROUTINE_SCOPE.launch(UI_DISPATCHER) {
                onProgressUpdate(*values)
            }
        }
    }

    fun get(): Result? {
        lock.withLock {
            while (status != Status.FINISHED) {
                executionCompletedCondition.await()
            }
        }
        val exception = executionException.get()
        if (exception != null) {
            throw exception
        } else {
            return executionResult.get()
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        _isCancelled.set(true)
        val job = coroutineJob.get() ?: return true
        return if (job.isCompleted || job.isCancelled) {
            false
        } else {
            if (mayInterruptIfRunning) {
                job.cancel()
            }
            true
        }
    }

    private companion object {
        private val THREAD_COUNT = AtomicInteger(0)
        private val THREAD_FACTORY = ThreadFactory { runnable ->
            Thread(runnable, "CoroutinesAsyncTask #${THREAD_COUNT.getAndIncrement()}")
        }
        private val BACKGROUND_DISPATCHER = ThreadPoolExecutor(
            3,
            Int.MAX_VALUE,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue<Runnable>(),
            THREAD_FACTORY
        ).let {
            it.prestartCoreThread()
            it.asCoroutineDispatcher()
        }
        private val UI_DISPATCHER = Dispatchers.Main
        private val COROUTINE_SCOPE = CoroutineScope(SupervisorJob() + UI_DISPATCHER)
    }
}