*Optional*. The old name of the job. If configured, the history of old job will be inherited to the new one. Once the
pipeline is set, this field can be removed as the builds have been transferred.

This can be used to rename a job without losing its history, like so:

    jobs:
    - name: new-name
      old_name: current-name
      plan: [get: 10m]

After the pipeline is set, because the builds have been inherited, the job can have the field removed:

    jobs:
    - name: new-name
      plan: [get: 10m]

