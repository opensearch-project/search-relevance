# RFC: Learning to Rank model registry

## What/Why

### What are you proposing?

A Learning to Rank (LTR) model registry in OpenSearch Dashboards: a place to see which
models are deployed, inspect what they are, and upload a newly trained one. The registry
covers a list of the models in the LTR feature store, a detail view showing metadata plus
the feature set the model was built from and the raw model definition, an upload flow
wrapping `_createmodel`, and read access to feature sets for the upload and detail views.

It is one stage of a larger LTR workflow (pick features, train, deploy, A/B test with
interleaving), but it does not depend on the other stages shipping.

### What users have asked for this feature?

I do not have public issues or forum threads to cite for this one. The ask comes from
relevance engineering work on clusters running the LTR plugin, where model bookkeeping is
done by hand outside the cluster. If reviewers know of existing issues on
`opensearch-project/opensearch-learning-to-rank-base` or the forums, link them here and I
will fold them in.

### What problems are you trying to solve?

There is no UI for LTR models. Everything goes through raw REST calls against `_ltr/*`, and
the only way to deploy a trained model is a hand-written `_createmodel` call. Relevance
engineers track model names, feature sets, and store names by hand, outside the cluster.

- When taking over a cluster they did not configure, a relevance engineer wants to see the
  models in the feature store, so they can tell what is deployed without writing a script
  against `_ltr/_model`.
- When inspecting a model before using it in an `sltr` query, a relevance engineer wants to
  read its feature set and raw definition in one place, so they can confirm the model matches
  the features the query will supply.
- When deploying a model trained outside the cluster, a relevance engineer wants to submit it
  through a form, so they can avoid hand-writing a `_createmodel` body and the mistakes that
  come with it.

### What is the developer experience going to be?

No backend change is needed and no new REST API is added. The LTR plugin already stores
models in `.ltrstore` and exposes list and fetch endpoints for models, feature sets, and
features, plus `_createmodel`. This is a UI and metadata layer over those endpoints.

Two properties of the existing API shape the client:

- `GET /_ltr/_model` defaults to `size=20`. On a store with more than 20 models it still only
  returns 20 with no error, so any client has to send an explicit size.
- Multi-store is supported by the plugin (`_ltr/{store}/...`), but v1 uses the default store
  with no store selector. The store parameter is threaded through the service layer so adding
  a selector later would be a relatively easy UI change.

The store mapping is `dynamic: strict` and has no timestamp field. There is no `created_at`
to display, and adding one means a mapping change on the LTR plugin plus a backfill story for
existing stores. I propose leaving it out of v1 and deciding it separately.

#### Are there any security considerations?

`.ltrstore*` is a registered system index. Dashboards cannot read it directly and must go
through the plugin's `_ltr/*` endpoints, which is what this design does. Everything in scope
is reachable that way. Requests inherit the calling user's credentials through the standard
Dashboards proxy, so authorization stays with the LTR plugin and the security plugin. 
Model definitions are displayed as stored and are not evaluated in the browser.

#### Are there any breaking changes to the API

None. No existing endpoint changes and none are deprecated.

### What is the user experience going to be?

Net new functionality. There is no LTR screen in Dashboards today.

- A list of models in the feature store, showing model name, feature set name, and model
  type. Model types are `model/ranklib`, `model/linear`, `model/xgboost+json`, and
  `model/xgboost+json+raw`.
- A detail view per model: the same metadata, the feature set the model was built from, and
  the raw model definition.
- An upload flow: paste a model definition and attach it to an existing feature set.
- An explicit "LTR is not installed" state when `GET /_ltr` fails, matching how the existing
  search-relevance tabs handle a missing backend.

Registry entries come straight from the store with no Dashboards-side state.

#### Are there breaking changes to the User Experience?

No. Nothing existing changes. This adds screens.

### Why should it be built? Any reason not to?

Without it, the only answer to "what models are on this cluster?" is a script, and deploying
a model means hand-writing REST calls. The registry is also where the rest of the LTR
workflow attaches: feature selection, training, and interleaved A/B testing all need a way to
name and inspect a deployed model.

The reason not to build it in `dashboards-search-relevance` is coupling. The registry depends
on the LTR plugin but would ship with the search-relevance UI, so a cluster with one and not
the other gets a half-working screen. The "LTR is not installed" state above is the answer to
that. A separate plugin would duplicate the whole nav, routing, and test harness to avoid one
empty state.

### What will it take to execute?

Extend `dashboards-search-relevance`. That plugin already
has a regular per-resource layout (`views/`, `components/`, `hooks/`, `services/`,
`__tests__/`) that query sets, search configurations, and judgments all follow, and the
registry is the same list/detail/create shape. It has nav real estate. Its server route helper
proxies raw transport requests and is not tied to the `_plugins/_search_relevance` namespace,
so `_ltr/*` paths work through it unchanged. The feature selection stage should go in this
plugin anyway, so splitting the registry out would put a disconnect in the stages needed for LTR.

The list and detail views are implemented and tested against a live OpenSearch 3.3.0 cluster
with 28 seeded models. The upload flow is implemented and unit tested; it has not yet been
exercised against a live cluster.

Testing:

- The list matches the cluster, checked by diffing the UI's data against direct `_ltr/_model`
  calls.
- Definitions render without mangling. The store writes them either as a raw string (RankLib)
  or as embedded JSON (linear, XGBoost).
- Upload round-trips: a model created through the UI is retrievable and usable in an `sltr`
  query, the same as one created with curl.

Out of scope for v1:

- Training. Nothing in-cluster trains a model today, and this does not change that.
- A/B testing and interleaving.
- Versioning, lineage, rollback, champion/challenger. The schema these need is not knowable
  until training and A/B testing exist. The registry entry stays minimal and grows later.
- A feature set builder. Feature set authoring belongs with the feature selection stage.

### Any remaining open questions?

1. Does this document belong here, or in `dashboards-search-relevance` where the code will go?
2. Is the plugin coupling acceptable, or is an LTR-without-search-relevance cluster common
   enough to justify a separate plugin?
3. Is `created_at` worth a mapping change on the LTR plugin?
4. Versioning and champion/challenger are deferred. Any other metadata we need as
   the training and A/B testing stages firm up, since they set the registry entry's schema?
