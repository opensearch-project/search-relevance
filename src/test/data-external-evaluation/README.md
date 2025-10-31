This directory contains an example of an externally run evaluation being imported into SRW.  The original evaluation was run using the Quepid tool (https://github.com/o19s/quepid) and then exported.

1) Queries, Judgements, and Metrics sourced from a export from Quepid called  `Movie_Search_Quepid_1.json`. 

1) The `movies_queryset.json` was hand extracted.

1) The `movies_queryset.json` was done using the Python script `extract_quepid_judgements.py`.

1) The `movies_experiment_pointwise.json` was done using the Python script `extract_quepid_experiment.py` processing `Movie_Search_Quepid_1.json`.

1) The `movies_experiment_pairwise.json` was done using the Python script `generate_pairwise_experiment.py` processing `Movie_Search_Quepid_1.json` and `Movie_Search_Quepid_2.json`..
