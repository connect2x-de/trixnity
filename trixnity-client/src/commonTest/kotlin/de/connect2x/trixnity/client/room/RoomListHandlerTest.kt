package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.MatrixClientConfiguration.DeleteRooms
import de.connect2x.trixnity.client.getInMemoryGlobalAccountDataStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.getInMemoryServerDataStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.client.simpleUserInfo
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomDisplayName
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.LeftRoom
import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap.Companion.roomMapOf
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test

class RoomListHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val roomId = RoomId("!room:server")

    private val user1 = UserId("user1", "server")
    private val user2 = UserId("user2", "server")
    private val user3 = UserId("user3", "server")
    private val user4 = UserId("user4", "server")
    private val user5 = UserId("user5", "server")

    private val roomStore = getInMemoryRoomStore()
    private val roomStateStore = getInMemoryRoomStateStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()
    private val serverDataStore = getInMemoryServerDataStore()

    private val config = MatrixClientConfiguration()
    private val forgetRooms = mutableListOf<RoomId>()
    private val api = mockMatrixClientServerApiClient()
    private val roomServiceMock = RoomServiceMock()

    private val cut = RoomListHandler(
        api = api,
        roomStore = roomStore,
        roomStateStore = roomStateStore,
        globalAccountDataStore = globalAccountDataStore,
        serverDatastore = serverDataStore,
        forgetRoomService = { roomId, _ -> forgetRooms.add(roomId) },
        roomService = roomServiceMock,
        userInfo = simpleUserInfo,
        tm = TransactionManagerMock(),
        config = config,
    )

    private val createEvent = StateEvent(
        CreateEventContent(),
        EventId("event1"),
        UserId("user1", "localhost"),
        roomId,
        0,
        stateKey = ""
    )


    @Test
    fun `updateRoomList » lastRelevantEventId » setlastRelevantEventId`() = runTest {
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    room = Sync.Response.Rooms(
                        join = roomMapOf(
                            roomId to JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(
                                        createEvent,
                                        MessageEvent(
                                            RoomMessageEventContent.TextBased.Text("Hello!"),
                                            EventId("event2"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            5,
                                        ),
                                        StateEvent(
                                            AvatarEventContent("mxc://localhost/123456"),
                                            EventId("event3"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            10,
                                            stateKey = ""
                                        ),
                                    ), previousBatch = "abcdef"
                                )
                            )
                        )
                    ), nextBatch = "123456"
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.lastRelevantEventId shouldBe EventId("event2")
    }

    @Test
    fun `updateRoomList » lastEventId » must not update `() = runTest {
        roomStore.update(roomId) { simpleRoom.copy(lastEventId = EventId("old")) }
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    room = Sync.Response.Rooms(
                        join = roomMapOf(
                            roomId to JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(
                                        createEvent,
                                        MessageEvent(
                                            RoomMessageEventContent.TextBased.Text("Hello!"),
                                            EventId("event2"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            5,
                                        ),
                                        StateEvent(
                                            AvatarEventContent("mxc://localhost/123456"),
                                            EventId("event3"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            10,
                                            stateKey = ""
                                        ),
                                    ), previousBatch = "abcdef"
                                )
                            )
                        )
                    ), nextBatch = "123456"
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.lastEventId shouldBe EventId("old")
    }

    @Test
    fun `updateRoomList » name » keep when no change`() = runTest {
        roomStore.update(roomId) {
            simpleRoom.copy(
                lastEventId = createEvent.id,
                name = RoomDisplayName("NAME", summary = null),
                createEventContent = createEvent.content
            )
        }
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(join = roomMapOf(roomId to JoinedRoom())),
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.name shouldNotBe null
    }

    @Test
    fun `isDirect » set the room to direct == 'true' when a DirectEventContent is found for the room`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, isDirect = false) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(RoomId("!room2:localhost"), roomId)
                )
            )
            roomStore.getAll().first { it.size == 1 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.isDirect shouldBe true
        }

    @Test
    fun `isDirect » membership is LEAVE or BAN » don't change isDirect`() =
        runTest {
            val roomId2 = RoomId("!room2:server")
            roomStore.update(roomId) { Room(roomId, isDirect = true, membership = Membership.LEAVE) }
            roomStore.update(roomId2) { Room(roomId2, isDirect = true, membership = Membership.BAN) }
            val eventContent = DirectEventContent(
                mappings = mapOf()
            )
            roomStore.getAll().first { it.size == 2 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.isDirect shouldBe true
            roomStore.get(roomId2).first()?.isDirect shouldBe true
        }

    @Test
    fun `isDirect » set the room to direct == 'false' when no DirectEventContent is found for the room`() =
        runTest {
            val room1 = RoomId("!room1:localhost")
            val room2 = RoomId("!room2:localhost")
            val roomStore = roomStore

            roomStore.update(room1) { Room(room1, isDirect = true) }
            roomStore.update(room2) { Room(room2, isDirect = true) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(room2)
                )
            )
            roomStore.getAll().first { it.size == 2 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(room1).first()?.isDirect shouldBe false
            roomStore.get(room2).first()?.isDirect shouldBe true
        }


    @Test
    fun `avatarUrl » room is direct » set the avatar URL to a member's avatar URL`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = null) }
        roomStateStore.save(
            StateEvent(
                MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                EventId("1"),
                bob,
                roomId,
                0L,
                stateKey = alice.full,
            )
        )
        val eventContent = DirectEventContent(
            mappings = mapOf(
                alice to setOf(
                    roomId,
                    RoomId("!room2:localhost")
                )
            )
        )

        cut.updateRoomList(
            SyncEvents(
                Sync.Response(""),
                listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
            )
        )

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
    }

    @Test
    fun `avatarUrl » membership is LEAVE or BAN » don't change avatar'`() = runTest {
        val roomId2 = RoomId("!room2:localhost")
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef", membership = Membership.LEAVE) }
        roomStore.update(roomId2) { Room(roomId2, avatarUrl = "mxc://localhost/abcdef", membership = Membership.BAN) }
        val eventContent = DirectEventContent(
            mappings = mapOf()
        )

        cut.updateRoomList(
            SyncEvents(
                Sync.Response(""),
                listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
            )
        )

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
        roomStore.get(roomId2).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
    }

    @Test
    fun `avatarUrl » room is direct » update the room's avatar URL`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/old") }
        val event1 = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/right",
                membership = Membership.JOIN,
            ),
            EventId("1"),
            alice,
            roomId,
            0L,
            stateKey = alice.full,
        )
        val event2 = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/wrong",
                membership = Membership.JOIN,
            ),
            EventId("2"),
            alice,
            roomId,
            0L,
            stateKey = bob.full,
        )
        globalAccountDataStore.save(
            ClientEvent.GlobalAccountDataEvent(
                DirectEventContent(mappings = mapOf(alice to setOf(roomId)))
            )
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event1, event2)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/right"
    }

    @Test
    fun `avatarUrl » room is direct » when the avatar URL is explicitly set use it instead of member's avatar URL`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/123456") }
            roomStateStore.save(
                StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = alice.full,
                )
            )
            roomStateStore.save(
                StateEvent(
                    AvatarEventContent("mxc://localhost/123456"),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = "",
                )
            )
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    alice to setOf(
                        roomId,
                        RoomId("!room2:localhost")
                    )
                )
            )

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }

    @Test
    fun `avatarUrl » room is direct » set the avatar URL to a member of a direct room when the new avatar URL is empty`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, isDirect = true, avatarUrl = "mxc://localhost/abcdef") }
            globalAccountDataStore.save(
                ClientEvent.GlobalAccountDataEvent(
                    DirectEventContent(mappings = mapOf(bob to setOf(roomId, RoomId("!room2:localhost"))))
                )
            )
            roomStateStore.save(
                StateEvent(
                    MemberEventContent(
                        avatarUrl = "mxc://localhost/123456",
                        membership = Membership.JOIN
                    ),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = bob.full
                )
            )
            val event = StateEvent(
                AvatarEventContent(""),
                EventId("1"),
                bob,
                roomId,
                0L,
                stateKey = bob.full,
            )

            cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

            roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }


    @Test
    fun `avatarUrl » room id not direct » do nothing on member event`() = runTest {
        roomStore.update(roomId) { Room(roomId) }
        val event = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/123456",
                membership = Membership.JOIN,
            ),
            EventId("1"),
            alice,
            roomId,
            0L,
            stateKey = alice.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe null
    }

    @Test
    fun `avatarUrl » room id not direct » set the avatar URL for normal rooms`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef") }
        val event = StateEvent(
            AvatarEventContent("mxc://localhost/123456"),
            EventId("1"),
            bob,
            roomId,
            0L,
            stateKey = bob.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
    }

    @Test
    fun `avatarUrl » room id not direct » set an empty avatar URL for normal rooms`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef") }
        val event = StateEvent(
            AvatarEventContent(""),
            EventId("1"),
            bob,
            roomId,
            0L,
            stateKey = bob.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe null
    }

    @Test
    fun `displayName » do nothing when room summary did not change at all`() = runTest {
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 2,
        )
        val roomBefore = simpleRoom.copy(name = RoomDisplayName(explicitName = "bla", summary = roomSummary))
        roomStore.update(roomId) { roomBefore }
        cut.calculateDisplayName(roomId, summary = roomSummary, membership = Membership.JOIN) shouldBe null
    }

    @Test
    fun `displayName » name event given » set explicit name`() = runTest {
        cut.calculateDisplayName(
            roomId,
            nameEventContent = NameEventContent("explicit"),
            summary = JoinedRoom.RoomSummary(),
            membership = Membership.JOIN
        ).shouldNotBeNull { explicitName shouldBe "explicit" }
    }

    @Test
    fun `displayName » name event found in store » set explicit name`() = runTest {
        roomStateStore.save(nameEvent(1, user1, "explicit"))
        cut.calculateDisplayName(roomId, summary = JoinedRoom.RoomSummary(), membership = Membership.JOIN)
            .shouldNotBeNull { explicitName shouldBe "explicit" }
    }

    @Test
    fun `displayName » name alias event given » set explicit name`() = runTest {
        cut.calculateDisplayName(
            roomId,
            canonicalAliasEventContent = CanonicalAliasEventContent(RoomAliasId("#explicit:room")),
            summary = JoinedRoom.RoomSummary(),
            membership = Membership.JOIN,
        ).shouldNotBeNull { explicitName shouldBe "#explicit:room" }
    }

    @Test
    fun `displayName » name alias event found in store » set explicit name`() = runTest {
        roomStateStore.save(canonicalAliasEvent(1, user1, RoomAliasId("#explicit:room")))
        cut.calculateDisplayName(roomId, summary = JoinedRoom.RoomSummary(), membership = Membership.JOIN)
            .shouldNotBeNull { explicitName shouldBe "#explicit:room" }
    }

    @Test
    fun `displayName » name event given » name event is empty » use alias event`() = runTest {
        cut.calculateDisplayName(
            roomId,
            nameEventContent = NameEventContent(""),
            canonicalAliasEventContent = CanonicalAliasEventContent(RoomAliasId("#explicit:room")),
            summary = JoinedRoom.RoomSummary(),
            membership = Membership.JOIN,
        ).shouldNotBeNull { explicitName shouldBe "#explicit:room" }
    }

    @Test
    fun `displayName » name and alias event given » both empty » explicit name is null`() = runTest {
        cut.calculateDisplayName(
            roomId,
            nameEventContent = NameEventContent(""),
            canonicalAliasEventContent = CanonicalAliasEventContent(RoomAliasId("")),
            summary = JoinedRoom.RoomSummary(),
            membership = Membership.JOIN,
        ).shouldNotBeNull { explicitName shouldBe null }
    }

    @Test
    fun `displayName » no name event found » joined and invited count le 5 » other users count is 0`() = runTest {
        val summary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2, user3, user4, user5),
            joinedMemberCount = 3,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
            explicitName = null,
            heroes = listOf(user1, user2, user3, user4, user5),
            otherUsersCount = 0,
            isEmpty = false,
            summary = summary,
        )
    }

    @Test
    fun `displayName » no name event found » joined and invited count is 6 » we are part of server heroes and thus other users count is 1 and heroes does not include us`() =
        runTest {
            val summary = JoinedRoom.RoomSummary(
                heroes = listOf(user1, user2, user3, simpleUserInfo.userId, user4),
                joinedMemberCount = 4,
                invitedMemberCount = 2,
            )
            cut.calculateDisplayName(
                roomId,
                summary = summary,
                membership = Membership.INVITE
            ) shouldBe RoomDisplayName(
                explicitName = null,
                heroes = listOf(user1, user2, user3, user4), // without me
                otherUsersCount = 1, // 4 heroes + 1 me + 1 other
                isEmpty = false,
                summary = summary,
            )
        }

    @Test
    fun `displayName » no name event found » joined and invited count is 8 » other users count is 2`() =
        runTest {
            val summary = JoinedRoom.RoomSummary(
                heroes = listOf(user1, user2, user3, user4, user5),
                joinedMemberCount = 6,
                invitedMemberCount = 2,
            )
            cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
                explicitName = null,
                heroes = listOf(user1, user2, user3, user4, user5),
                otherUsersCount = 2, // 5 heroes + 1 me + 2 others
                isEmpty = false,
                summary = summary,
            )
        }

    @Test
    fun `displayName » no name event found » we left the room » other users count is correct`() = runTest {
        val summary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2, user3, user4, user5),
            joinedMemberCount = 5,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, summary = summary, membership = Membership.LEAVE) shouldBe RoomDisplayName(
            explicitName = null,
            heroes = listOf(user1, user2, user3, user4, user5),
            otherUsersCount = 2, // 5 heroes + 2 others (we are not part of the room anymore
            isEmpty = false,
            summary = summary,
        )
    }

    @Test
    fun `displayName » no name event found » no explicit heroes set » use joined members as heroes excluding us`() =
        runTest {
            listOf(
                memberEvent(1, user1, "User1-Display", Membership.JOIN),
                memberEvent(2, user2, "User2-Display", Membership.INVITE),
                memberEvent(3, user3, "User3-Display", Membership.JOIN),
                memberEvent(4, user4, "User4-Display", Membership.INVITE),
                memberEvent(5, simpleUserInfo.userId, "me-Display", Membership.JOIN),
            ).forEach { roomStateStore.save(it) }
            val summary = JoinedRoom.RoomSummary(
                joinedMemberCount = 3,
                invitedMemberCount = 2,
            )
            cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
                explicitName = null,
                heroes = listOf(user1, user2, user3, user4),
                otherUsersCount = 0,
                isEmpty = false,
                summary = summary,
            )
        }

    @Test
    fun `displayName » no name event found » no explicit heroes set » no joined members » use left members as heroes`() =
        runTest {
            listOf(
                memberEvent(1, user1, "User1-Display", Membership.LEAVE),
                memberEvent(2, user2, "User2-Display", Membership.LEAVE),
                memberEvent(3, user3, "User3-Display", Membership.BAN),
                memberEvent(4, user4, "User4-Display", Membership.LEAVE),
                memberEvent(5, simpleUserInfo.userId, "me-Display", Membership.JOIN),
            ).forEach { roomStateStore.save(it) }
            val summary = JoinedRoom.RoomSummary(
                joinedMemberCount = 1,
                invitedMemberCount = 0,
            )
            cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
                explicitName = null,
                heroes = listOf(user1, user2, user3, user4),
                otherUsersCount = 0,
                isEmpty = true,
                summary = summary,
            )
        }

    @Test
    fun `displayName » no name event found » no joined or left members » mark as empty`() = runTest {
        val summary = JoinedRoom.RoomSummary(
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
            explicitName = null,
            heroes = emptyList(),
            otherUsersCount = 0,
            isEmpty = true,
            summary = summary,
        )
    }

    @Test
    fun `displayName » no name event found » joined members is gt 1 » isEmpty is false`() = runTest {
        listOf(
            memberEvent(1, user1, "User1-Display", Membership.LEAVE),
            memberEvent(2, user2, "User2-Display", Membership.LEAVE),
            memberEvent(3, user3, "User3-Display", Membership.BAN),
            memberEvent(4, user4, "User4-Display", Membership.INVITE),
            memberEvent(5, simpleUserInfo.userId, "me-Display", Membership.JOIN),
        ).forEach { roomStateStore.save(it) }
        val summary = JoinedRoom.RoomSummary(
            joinedMemberCount = 1,
            invitedMemberCount = 1,
        )
        cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
            explicitName = null,
            heroes = listOf(user4),
            otherUsersCount = 0,
            isEmpty = false,
            summary = summary,
        )
    }

    @Test
    fun `displayName » no name event found » joined members is eq 1 » isEmpty is true`() = runTest {
        listOf(
            memberEvent(1, user1, "User1-Display", Membership.LEAVE),
            memberEvent(2, user2, "User2-Display", Membership.LEAVE),
            memberEvent(3, user3, "User3-Display", Membership.BAN),
            memberEvent(4, user4, "User4-Display", Membership.LEAVE),
            memberEvent(5, simpleUserInfo.userId, "me-Display", Membership.JOIN),
        ).forEach { roomStateStore.save(it) }
        val summary = JoinedRoom.RoomSummary(
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, summary = summary, membership = Membership.JOIN) shouldBe RoomDisplayName(
            explicitName = null,
            heroes = listOf(user1, user2, user3, user4),
            otherUsersCount = 0,
            isEmpty = true,
            summary = summary,
        )
    }

    @Test
    fun `displayName » no name event found » joined members is eq 1 » summary does not set it » isEmpty is true`() =
        runTest {
            listOf(
                memberEvent(1, user1, "User1-Display", Membership.INVITE),
                memberEvent(2, simpleUserInfo.userId, "me-Display", Membership.JOIN),
            ).forEach { roomStateStore.save(it) }
            val roomSummary = JoinedRoom.RoomSummary(
                heroes = listOf(),
                joinedMemberCount = null,
                invitedMemberCount = null,
            )
            val roomBefore = simpleRoom.copy(
                name = RoomDisplayName(
                    explicitName = null,
                    summary = roomSummary.copy(joinedMemberCount = 0)
                )
            )
            roomStore.update(roomId) { roomBefore }
            cut.calculateDisplayName(roomId, summary = roomSummary, membership = Membership.JOIN) shouldNotBeNull {
                otherUsersCount shouldBe 1
                isEmpty shouldBe false
            }
        }

    @Test
    fun `displayName » merge summary correctly`() = runTest {
        val oldSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user2),
            joinedMemberCount = 4,
            invitedMemberCount = 1,
        )
        val roomBefore = simpleRoom.copy(
            name = RoomDisplayName(
                summary = oldSummary,
            )
        )
        roomStore.update(roomId) { roomBefore }
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1),
            joinedMemberCount = 3,
            invitedMemberCount = null,
        )
        cut.calculateDisplayName(roomId, summary = roomSummary, membership = Membership.JOIN) shouldNotBeNull {
            summary shouldBe JoinedRoom.RoomSummary(
                heroes = listOf(user1),
                joinedMemberCount = 3,
                invitedMemberCount = 1,
            )
        }
    }


    @Test
    fun `deleteLeftRooms » forget rooms on leave when activated`() = runTest {
        config.deleteRooms = DeleteRooms.OnLeave
        cut.deleteLeftRooms(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = roomMapOf(roomId to LeftRoom())
                    )
                ),
                emptyList()
            )
        )

        forgetRooms shouldBe listOf(roomId)
    }

    @Test
    fun `deleteLeftRooms » not forget rooms on leave when disabled`() = runTest {
        config.deleteRooms = DeleteRooms.Never
        roomStore.update(roomId) { simpleRoom.copy(membership = Membership.LEAVE) }

        roomStore.getAll().first { it.size == 1 }

        cut.deleteLeftRooms(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = roomMapOf(roomId to LeftRoom())
                    )
                ), emptyList()
            )
        )

        roomStore.get(roomId).first() shouldNotBe null
    }

    @Test
    fun `deleteLeftRooms » do forget rooms where timeline is empty`() =
        runTest {
            config.deleteRooms = DeleteRooms.WhenNotJoined
            val room = simpleRoom.copy(membership = Membership.LEAVE)
            roomStore.update(roomId) { room }
            roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf())

            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = roomMapOf(roomId to LeftRoom())
                        )
                    ),
                    allEvents = listOf()
                )
            )

            forgetRooms shouldBe listOf(roomId)
        }

    @Test
    fun `deleteLeftRooms » do not forget rooms where there is a timeline since we joined the room in the past`() =
        runTest {
            config.deleteRooms = DeleteRooms.WhenNotJoined
            val room = simpleRoom.copy(membership = Membership.LEAVE)
            roomStore.update(roomId) { room }
            roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
            roomServiceMock.returnGetTimelineEvents = flowOf(
                flowOf(
                    TimelineEvent(
                        event = MessageEvent(
                            content = RoomMessageEventContent.TextBased.Text(body = "hey"),
                            id = checkNotNull(room.lastEventId),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 1L,
                        ),
                        previousEventId = null,
                        nextEventId = null,
                        gap = null,
                    )
                )
            )

            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = roomMapOf(roomId to LeftRoom())
                        )
                    ),
                    allEvents = listOf()
                )
            )

            forgetRooms shouldBe emptyList()
        }

    @Test
    fun `deleteLeftRooms » do not forget rooms where there is a JOIN event for us`() =
        runTest {
            config.deleteRooms = DeleteRooms.WhenNotJoined
            val room = simpleRoom.copy(membership = Membership.LEAVE)
            roomStore.update(roomId) { room }
            roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
            roomServiceMock.returnGetTimelineEvents = flowOf(
                flowOf(
                    TimelineEvent(
                        event = StateEvent(
                            MemberEventContent(membership = Membership.JOIN),
                            id = checkNotNull(room.lastEventId),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 0L,
                            stateKey = UserId("me", "server").full,
                        ),
                        previousEventId = null,
                        nextEventId = null,
                        gap = null,
                    )
                )
            )
            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = roomMapOf(
                                roomId to LeftRoom()
                            )
                        )
                    ),
                    allEvents = listOf()
                )
            )

            forgetRooms shouldBe emptyList()
        }

    @Test
    fun `deleteLeftRooms » state event of other user found in room so do NOT delete`() =
        runTest {
            config.deleteRooms = DeleteRooms.WhenNotJoined
            val room = simpleRoom.copy(membership = Membership.LEAVE)
            roomStore.update(roomId) { room }
            roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
            roomServiceMock.returnGetTimelineEvents = flowOf(
                flowOf(
                    TimelineEvent(
                        event = StateEvent(
                            AvatarEventContent(url = "mxc://server.local"),
                            id = checkNotNull(room.lastEventId),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 0L,
                            stateKey = user1.full,
                        ),
                        previousEventId = null,
                        nextEventId = null,
                        gap = null,
                    ),
                )
            )

            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = roomMapOf(roomId to LeftRoom())
                        )
                    ),
                    allEvents = listOf()
                )
            )

            forgetRooms shouldBe emptyList()
        }

    @Test
    fun `deleteLeftRooms » only own state events found in room so do delete`() = runTest {
        config.deleteRooms = DeleteRooms.WhenNotJoined
        val room = simpleRoom.copy(membership = Membership.LEAVE)
        roomStore.update(roomId) { room }
        roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
        roomServiceMock.returnGetTimelineEvents = flowOf(
            flowOf(
                TimelineEvent(
                    event = StateEvent(
                        MemberEventContent(membership = Membership.BAN),
                        id = EventId("1"),
                        sender = user1,
                        roomId = roomId,
                        originTimestamp = 0L,
                        stateKey = UserId("me", "server").full,
                    ),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null,
                ),
            )
        )

        cut.deleteLeftRooms(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = roomMapOf(roomId to LeftRoom())
                    )
                ),
                allEvents = listOf()
            )
        )

        forgetRooms shouldBe listOf(roomId)
    }

    @Test
    fun `deleteLeftRooms » config is set retain all rooms » do not delete left rooms that were never joined`() =
        runTest {
            config.deleteRooms = DeleteRooms.Never
            val room = simpleRoom.copy(membership = Membership.LEAVE)
            roomStore.update(roomId) { room }
            roomServiceMock.rooms.value = mapOf(roomId to MutableStateFlow(room))
            roomServiceMock.returnGetTimelineEvents = flowOf(
                flowOf(
                    TimelineEvent(
                        event = StateEvent(
                            MemberEventContent(membership = Membership.BAN),
                            id = EventId("1"),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 0L,
                            stateKey = UserId("me", "server").full,
                        ),
                        previousEventId = null,
                        nextEventId = null,
                        gap = null,
                    )
                )
            )

            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = roomMapOf(
                                roomId to LeftRoom()
                            )
                        )
                    ),
                    allEvents = listOf()
                )
            )

            forgetRooms shouldBe emptyList()
        }

    private fun memberEvent(
        i: Long,
        userId: UserId,
        displayName: String,
        membership: Membership
    ): StateEvent<MemberEventContent> {
        return StateEvent(
            MemberEventContent(
                displayName = displayName,
                membership = membership
            ),
            EventId("\$event$i"),
            userId,
            roomId,
            i,
            stateKey = userId.full
        )
    }

    private fun nameEvent(
        i: Long,
        userId: UserId,
        name: String
    ): StateEvent<NameEventContent> {
        return StateEvent(
            NameEventContent(name),
            EventId("\$event$i"),
            userId,
            roomId,
            i,
            stateKey = ""
        )
    }

    private fun canonicalAliasEvent(
        i: Long,
        userId: UserId, roomAliasId: RoomAliasId
    ): StateEvent<CanonicalAliasEventContent> {
        return StateEvent(
            CanonicalAliasEventContent(roomAliasId),
            EventId("\$event$i"),
            userId,
            roomId,
            1,
            stateKey = ""
        )
    }
}



