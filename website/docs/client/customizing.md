---
sidebar_position: 15
---

# Customizing

In order to customize the `MatrixClient`, you can override the implementation of several interfaces via dependency
injection (DI).

## Dependency Injection

```kotlin
matrixClient.create(...){
    modulesFactories += ::createCustomModule
}

fun createCustomModule() = module {
    single<MatrixClientServerApiClientFactory> {
        object : MatrixClientServerApiClientFactory {
            override fun create(
                authProvider: MatrixClientAuthProvider,
                eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
                json: Json = createMatrixEventJson(eventContentSerializerMappings),
                syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
                syncErrorDelayConfig: RetryFlowDelayConfig = RetryFlowDelayConfig.sync,
                coroutineContext: CoroutineContext = Dispatchers.Default,
                asUserId: UserId? = null,
                asDeviceId: String? = null,
                httpClientEngine: HttpClientEngine? = null,
                httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
            ): MatrixClientServerApiClient {
                // your code here
            }
            
            // ...
        }
    }
}
```

Be aware to always create new modules because a module saves your class instances and therefore is reused, which usually
is not wanted!

## Custom events

One goal in developing Trixnity was the ability to add custom events. This is achieved by mapping event types to
serializers. In this example,
we added a new message event: `de.connect2x.dino`. As a developer, you can then receive events of this Kotlin type like
all other default events.

```kotlin
@Serializable
data class DinoEventContent(
    val dinoCount: Long,
) : MessageEventContent {
    // just ignore these properties when you don't need them
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null
}

val customMappings = EventContentSerializerMappings { // add this to the modulesFactories (see example above)
    messageOf<DinoEventContent>("de.connect2x.dino")
}
```

It is also possible to add custom extensible events and content blocks. They also provide backwards compatibility via
`legacy`. For simplicity the latter is ignored in this example. If you want to support legacy events, take a look at
`TopicEventContent`.

```kotlin
@Serializable
@JvmInline
value class DinoContentBlock(
    private val dinoCount: Long,
) : EventContentBlock.Default {
    override val type: EventContentBlock.Type<*> get() = Type

    companion object Type : EventContentBlock.Type<DinoContentBlock> {
        override val value: String = "de.connect2x..dino"
    }
}

@Serializable(DinoEventContent.Serializer::class)
data class DinoEventContent(
    override val blocks: EventContentBlocks,
) : StateEventContent, ExtensibleEventContent<ExtensibleEventContent.Legacy.None> {
    // this is just a convenience constructor
    constructor(dinoCount: Long) : this(
        EventContentBlocks(DinoContentBlock(dinoCount)),
    )

    val dinoCount: Long? = blocks[DinoContentBlock]?.dinoCount
    override val legacy: ExtensibleEventContent.Legacy.None? = null
    override val externalUrl: String? = null

    object Serializer : ExtensibleEventContentSerializer<ExtensibleEventContent.Legacy.None, DinoEventContent>(
        "DinoEventContent",
        { blocks: EventContentBlocks, _ -> DinoEventContent(blocks) },
        ExtensibleEventContent.Legacy.None.serializer()
    )
}
```

In order to register your custom events, you can use `CustomEventContentSerializerMappings`:

```kotlin
matrixClient.create(...){
    modulesFactories += ::myEventsModule
}

fun myEventsModule() = module {
    single<CustomEventContentSerializerMappings>(named("MyCustomEventContentSerializerMappings")) {
        CustomEventContentSerializerMappings {
            blockOf(DinoContentBlock)
            messageOf<DinoEventContent>("de.connect2x.dino")
        }
    }
}
```

Be aware to always use a uniquely named binding so that multiple `CustomEventContentSerializerMappings`do not override
each other accidentally!
