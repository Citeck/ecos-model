# Диаграмма Ганта

На фронтэнде используется библиотека [Svar React Gantt](https://svar.dev/react/gantt/).

На бэкенде настройки диаграммы сохраняются в `emodel/gantt-settings` dao, которая связана с `emodel/gantt-data`.

**Схема данных**

```mermaid
graph TB
    A([Dashboard with Gantt widget]) --> B{Gantt Settings}
    B --> C[Type: STANDALONE]
    B --> D[Type: LINKED]
    C --> E[(Gantt Data)]
    E --> F[Activities]
    E --> G[Dependencies]
    D --> H[Linked Data Source]
    H --> I[Data Source ID] -->|Query| J(Custom logic)
```

Виджет диаграммы можно добавлять на различные дашборды - рабочее пространство, тип данных, при этом, на один дашборд
можно добавить несколько виджетов с разными настройками.

## Добавление и настройка диаграммы

При добавлении виджета на дашборд, пользователь может выбрать одну из настроенных диаграмм Ганта ранее,
либо создать новую.

При создании новой диаграммы, пользователь может настроить следующие параметры:

### Тип данных диаграммы

**dataType** - Тип данных диаграммы, выбор из значений:

- `STANDALONE` - автономная диаграмма, все данные вводятся вручную
- `LINKED` - связь с бизнес-объектом, построение и работа диаграммы происходит на основе данных бизнес-объекта,
  связанных по источнику данных (sourceId)

### Если выбран тип `LINKED`, то дополнительно можно настроить:

#### Источник данных

Выбор источника данных, к которому необходимо обращаться для получения и сохранения данных диаграммы.
Доступно _ручное значение_ или _выбор из доступных_. На форме выбор одного, должен исключать выбор другого.

**dataSourceId** - Источник данных, выбор из существующих источников:

- `ecos-project-tracker/gantt-pm` - Управление проектами

**manualDataSourceId** - Источник данных, ввод вручную. Текстовое поле.

#### Параметры построения диаграммы

**linkedWithType** - Тип данных, на основе которого будет строиться диаграмма, выбор из журнала типов данных.

**linkedWithRef** - RecordRef документа, на основе которого будет строиться диаграмма. Текстовое поле.

Поля `linkedWithType` и `linkedWithRef` используются для случая, когда диаграмма Ганта добавляется на любой дашборд,
но при этом должна строиться на основе определенных данных.

При добавлении на дашборд диаграммы, необходимо создать объект `emodel/gantt-settings` и сохранить на него ссылку в
дашборде.

[Пример запроса по созданию gant-settings](./gantt-settings-create.http)

### Ручной ввод данных

Пользователь настраивает диаграмму полностью сам - создает задачи, связи, при этом на бэкенд отправляются запросы
мутации.

Объект `emodel/gantt-settings` содержит ассоциацию к данным диаграммы - `emodel/gantt-data`, атрибут `data`.

Объект `emodel/gantt-data` содержит множественные дочерние ассоциации на объекты `emodel/gantt-activity` (
`timeline:activities`)
и `emodel/gantt-dependency` (`timeline:dependencies`).

```mermaid
graph TB
    A[Gantt Settings] -->|assoc| B[Gantt Data]
    B -->|multiple child assoc| C[Gantt Activity]
    B -->|multiple child assoc| D[Gantt Dependency]
```

| Entity     | Описание Svar React Gantt                                                                | Атрибуты                        | Пример Records API                                       |
|------------|------------------------------------------------------------------------------------------|---------------------------------|----------------------------------------------------------|
| Activity   | [Task description in svar-gantt](https://docs.svar.dev/react/gantt/api/properties/tasks) | Смотри аспект `activity-atts`   | [Create/Edit Activity](./gantt-activity-create.http)     |
| Dependency | [Link description in svar-gantt](https://docs.svar.dev/react/gantt/api/properties/links) | Смотри аспект `dependency-atts` | [Create/Edit Dependency](./gantt-dependency-create.http) |

### Вычисляемые данные

При выборе типа диаграммы `LINKED`, данные для диаграммы будут получаться из источника данных,
указанного в `dataSourceId` или `manualDataSourceId`.

При загрузке диаграммы, на сервер должен отправить query запрос на получение данных диаграммы, обязательно при каждом query
необходимо передавать информацию (если она есть) о текущем контексте, в котором открывается диаграмма:

- `linkedWithType`
- `linkedWithRef`
- `currentRef` - recordRef текущего документа
- `workspace` - текущий workspace

#### Примеры запросов

##### Получение данных для отображения диаграммы

[gantt-linked-query.http](./gantt-linked-query.http)

Ответа сервера:

```json
{
    "messages": [],
    "records": [
        {
            "id": "ecos-project-tracker/gantt-pm@f5aa520b-9746-4f71-bc72-fa27c36322c7",
            "attributes": {
                "title": "Задача 1",
                "description": "<p>описание 1</p>",
                "type": "task"
            }
        },
        {
            "id": "ecos-project-tracker/gantt-pm@8defa6a0-4995-45a9-a856-4931c8aef39e",
            "attributes": {
                "title": "Задача 2",
                "description": "<p>описание 2</p>",
                "type": "task"
            }
        },
        {
            "id": "ecos-project-tracker/gantt-pm@bd000c6b-1c77-4fdf-9fef-ec36857a95f2",
            "attributes": {
                "title": "Задача 3",
                "description": "<p>33</p>",
                "type": "task"
            }
        },
        {
            "id": "ecos-project-tracker/gantt-pm@cf386401-16cd-45e8-841c-0fee83faf35b",
            "attributes": {
                "title": "Этап 1",
                "description": null,
                "type": "summary"
            }
        },
        {
            "id": "ecos-project-tracker/gantt-pm@1778499d-fd7f-49ad-886a-5a0752444d49",
            "attributes": {
                "title": "Проработка",
                "description": null,
                "type": "milestone"
            }
        }
    ],
    "hasMore": false,
    "totalCount": 5,
    "version": 1
}
```

##### Создание новой активности (задачи/этапа/вехи) 

[gantt-linked-activity-create.http](./gantt-linked-activity-create.http)

##### Обновление активности (задачи/этапа/вехи)

[gantt-linked-activity-mutate.http](./gantt-linked-activity-mutate.http)

##### Удаление активности (задачи/этапа/вехи)

[gantt-linked-activity-delete.http](./gantt-linked-activity-delete.http)
