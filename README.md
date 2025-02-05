## testcontainers clone using ksp (including a sample test)

### how to use

- add the decorator @NeedsContainer
to the test class, including the container name (e.g. `NeedsContainer( name = "classname")`).
- add the decorator @ExtendsWith, including the generated class's name
  (e.g. `@ExtendsWith("ClassnameContainer.class")`, first letter capitalized)
- add a containers.json to the test's resource folder

The scheme of the containers.json is:

```
[
    {
        desc: string, 
        name: string, 
        file: string, 
        ports: string[]
    } ...
]
```

Make sure to implement a ready check, as the container startup typically takes some time.
Files are retrieved from the same folder the Dockerfile has been retrieved from.

Makes use of UDS, which has been added to Java with version 19.