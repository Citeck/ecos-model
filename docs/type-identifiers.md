# Type identifiers

A new type's local `TypeDef.id` must not contain `:`. This applies to both global
and workspace-scoped types, including creation through Records API, artifact
imports and cloning. The creation form reports the restriction in Russian or
English; `TypesService` enforces it independently of the form.

Workspace references still use `emodel/type@<workspace-system-id>:<local-type-id>`.
The colon in that reference is the workspace separator and is not part of the
local type ID. References to parents, forms and other artifacts keep this syntax.

Existing stored types with a colon in their local ID remain editable and can be
redeployed. This compatibility does not resolve their ambiguous references or
repair the registry synchronization issue described in COREDEV-551. This change
prevents creation of additional such types; it does not migrate existing data.
