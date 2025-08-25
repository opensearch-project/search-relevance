This directory contains an example of an externally run evaluation being imported into SRW.

1) Queries, Judgements, and Metrics sourced from a export from Quepid called  `Movie_Search_Quepid_Demo_case.json`. 

1) The `movies_queryset.json` was hand extracted.

1) The `movies_queryset.json` was done using the Python script `extract_quepid_judgements.py`.

1) The `movies_experiment.json` was done using the Python script `extract_quepid_experiment.py`.

When the `./scripts/demo_import_pointwise_experiment.sh` is run it creates a version of `movies_experiment.json` called 'movies_experiment_updated.json` that has the specific queryset, judgements and search configuration sourced from the running SRW.
