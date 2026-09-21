# Store Tasks app (Android, Kotlin)

Employee task execution app: log in, fetch tasks, capture Before/After photos,
complete tasks offline, and sync automatically when the network returns.

## Run it
1. Open this folder in Android Studio (Ladybug or newer, JDK 17).
2. Let Gradle sync, then run on an emulator or device.
   (If Studio asks about the Gradle wrapper, accept it. Version bumps it suggests are fine.)

## Demo employee IDs (mock API)
| ID    | Result                          |
|-------|---------------------------------|
| 1000  | 5 sample tasks (any 3+ char ID) |
| 2000  | Empty task list                 |
| 9999  | Login OK, task fetch returns 500|
| 0000  | Login rejected (401)            |

Try it: turn on airplane mode, finish a task, watch it show "Waiting to sync",
then turn airplane mode off. WorkManager uploads it and the status flips to "Completed".

## Architecture (MVVM, single source of truth)
```
UI (Compose)  ->  ViewModel (StateFlow)  ->  TaskRepository  ->  Room (truth)  <-  Retrofit (feeds Room)
                                                    \-> SyncScheduler -> SyncWorker (WorkManager)
```
- data/local   Room entity, DAO, photo file storage
- data/remote  Retrofit API + MockApiInterceptor (the fake backend)
- data/        TaskRepository, SessionStore
- sync/        WorkManager scheduler + worker
- ui/          login, tasks (list), detail screens + ViewModels
- di/          AppContainer (manual DI)

## Task lifecycle (LocalState)
NEW -> IN_PROGRESS (draft saved) -> PENDING_SYNC (completed, queued) -> SYNCED

- Refreshing from the server never overwrites local photos, notes, or state.
- Logging out keeps drafts and pending uploads.
- The UI reads only from Room, so it works the same online and offline.

## Swapping in a real backend
In `AppContainer`: set `USE_MOCK_API = false` and change `BASE_URL`.
Endpoints expected:
- POST /login                     {employeeId} -> {employeeId, name}
- GET  /employees/{id}/tasks      -> [{id,title,description,location,dueAt,status}]
- POST /tasks/{id}/complete       multipart: notes, completedAt, before, after (JPEG)
