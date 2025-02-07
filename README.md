## testcontainers clone using ksp (including a sample test)

### how to use

- add the decorator `@NeedsContainer`
to the test class, including the container name (e.g. `NeedsContainer( name = "ContainerName")`)
- add the decorator `@ExtendsWith`, including the generated class's name,
  e.g. `@ExtendsWith("ContainerNameDeployment.class")`
- add a `containers.json` to the test's resource folder

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

If you need multiple containers, just add a comma-separated list to the annotation.
E.g. `@NeedsContainer(name = 'ContainerA, ContainerB')`.
The resulting class file would be `ContainerAContainerBDeployment.class`.


Makes use of UDS, which has been added to Java with version 19.
And is tightly coupled with JUnit5, as it makes use of the `@ExtendsWith` decorator