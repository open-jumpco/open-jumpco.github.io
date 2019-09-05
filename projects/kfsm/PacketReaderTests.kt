/*
 * Copyright (c) 2019. Open JumpCO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.jumpco.open.kfsm

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Block {
    val byteArrayOutputStream = ByteArrayOutputStream(32)
    fun addByte(byte: Int) {
        byteArrayOutputStream.write(byte)
    }
}

interface ProtocolHandler {
    fun sendNACK()
    fun sendACK()
}

interface PacketHandler : ProtocolHandler {
    val checksumValid: Boolean
    fun print()
    fun addField()
    fun endField()
    fun addByte(byte: Int)
    fun addChecksum(byte: Int)
    fun checksum()
}

class ProtocolSender : ProtocolHandler {
    override fun sendNACK() {
        println("NACK")
    }

    override fun sendACK() {
        println("ACK")
    }
}

class Packet(private val protocolHandler: ProtocolHandler) : PacketHandler, ProtocolHandler by protocolHandler {
    val fields = mutableListOf<ByteArray>()
    private var currentField: Block? = null
    private var _checksumValid: Boolean = false

    override val checksumValid: Boolean
        get() = _checksumValid
    private val checkSum = Block()

    override fun print() {
        fields.forEachIndexed { index, bytes ->
            print("Packet:$index:")
            bytes.forEach { byte ->
                val hex = byte.toString(16).padStart(2, '0')
                print(" $hex")
            }
            println()
        }
    }
    override fun addField() {
        currentField = Block()
    }

    override fun endField() {
        val field = currentField
        require(field != null) { "expected currentField to have a value" }
        fields.add(field.byteArrayOutputStream.toByteArray())
        currentField = null
    }

    override fun addByte(byte: Int) {
        val field = currentField
        require(field != null) { "expected currentField to have a value" }
        field.addByte(byte)
    }

    override fun addChecksum(byte: Int) {
        checkSum.addByte(byte)
    }

    override fun checksum() {
        require(checkSum.byteArrayOutputStream.size() > 0)
        val checksumBytes = checkSum.byteArrayOutputStream.toByteArray()
        _checksumValid = if (checksumBytes.size == fields.size) {
            checksumBytes.mapIndexed { index, cs ->
                cs == fields[index][0]
            }.reduce { a, b -> a && b }
        } else {
            false
        }
    }
}

enum class ReaderEvents(val code: Int) {
    BYTE(0x00),
    SOH(0x01),
    STX(0x02),
    ETX(0x03),
    EOT(0x04),
    ESC(0x1b)
}

enum class ReaderStates {
    START,
    RCVPCKT,
    RCVDATA,
    RCVESC,
    RCVCHK,
    RCVCHKESC,
    CHKSUM,
    END
}

class PacketReaderFSM(private val packetHandler: PacketHandler) {
    companion object {
        private val definition = stateMachine(
            ReaderStates.values().toSet(),
            ReaderEvents::class,
            PacketHandler::class
        ) {
            initial { ReaderStates.START }
            default {
                transition(ReaderEvents.BYTE to ReaderStates.END) {
                    sendNACK()
                }
                transition(ReaderEvents.SOH to ReaderStates.END) {
                    sendNACK()
                }
                transition(ReaderEvents.STX to ReaderStates.END) {
                    sendNACK()
                }
                transition(ReaderEvents.ETX to ReaderStates.END) {
                    sendNACK()
                }
                transition(ReaderEvents.EOT to ReaderStates.END) {
                    sendNACK()
                }
                transition(ReaderEvents.ESC to ReaderStates.END) {
                    sendNACK()
                }
            }
            state(ReaderStates.START) {
                transition(ReaderEvents.SOH to ReaderStates.RCVPCKT) {}
            }
            state(ReaderStates.RCVPCKT) {
                transition(ReaderEvents.STX to ReaderStates.RCVDATA) {
                    addField()
                }
                transition(ReaderEvents.BYTE to ReaderStates.RCVCHK) { args ->
                    require(args.size == 1)
                    val byte = args[0] as Int
                    addChecksum(byte)
                }
            }
            state(ReaderStates.RCVDATA) {
                transition(ReaderEvents.BYTE) { args ->
                    require(args.size == 1)
                    val byte = args[0] as Int
                    addByte(byte)
                }
                transition(ReaderEvents.ETX to ReaderStates.RCVPCKT) {
                    endField()
                }
                transition(ReaderEvents.ESC to ReaderStates.RCVESC) {}
            }
            state(ReaderStates.RCVESC) {
                transition(ReaderEvents.ESC to ReaderStates.RCVDATA) {
                    addByte(ReaderEvents.ESC.code)
                }
                transition(ReaderEvents.STX to ReaderStates.RCVDATA) {
                    addByte(ReaderEvents.STX.code)
                }
                transition(ReaderEvents.SOH to ReaderStates.RCVDATA) {
                    addByte(ReaderEvents.SOH.code)
                }
                transition(ReaderEvents.ETX to ReaderStates.RCVDATA) {
                    addByte(ReaderEvents.ETX.code)
                }
                transition(ReaderEvents.EOT to ReaderStates.RCVDATA) {
                    addByte(ReaderEvents.EOT.code)
                }
            }
            state(ReaderStates.RCVCHK) {
                transition(ReaderEvents.BYTE) { args ->
                    require(args.size == 1)
                    val byte = args[0] as Int
                    addChecksum(byte)
                }
                transition(ReaderEvents.ESC to ReaderStates.RCVCHKESC) {}
                transition(ReaderEvents.EOT to ReaderStates.CHKSUM) {
                    checksum()
                }
            }
            state(ReaderStates.CHKSUM) {
                automatic(ReaderStates.END, guard = { !checksumValid }) {
                    sendNACK()
                }
                automatic(ReaderStates.END, guard = { checksumValid }) {
                    sendACK()
                }
            }
            state(ReaderStates.RCVCHKESC) {
                transition(ReaderEvents.ESC to ReaderStates.RCVCHK) {
                    addChecksum(ReaderEvents.ESC.code)
                }
                transition(ReaderEvents.SOH to ReaderStates.RCVCHK) {
                    addChecksum(ReaderEvents.SOH.code)
                }
                transition(ReaderEvents.EOT to ReaderStates.RCVCHK) {
                    addChecksum(ReaderEvents.EOT.code)
                }
                transition(ReaderEvents.STX to ReaderStates.RCVCHK) {
                    addChecksum(ReaderEvents.STX.code)
                }
                transition(ReaderEvents.ETX to ReaderStates.RCVCHK) {
                    addChecksum(ReaderEvents.ETX.code)
                }
            }
        }.build()
    }

    private val fsm = definition.create(packetHandler)
    fun receiveByte(byte: Int) {
        when (byte) {
            ReaderEvents.SOH.code -> fsm.sendEvent(ReaderEvents.SOH)
            ReaderEvents.STX.code -> fsm.sendEvent(ReaderEvents.STX)
            ReaderEvents.ETX.code -> fsm.sendEvent(ReaderEvents.ETX)
            ReaderEvents.EOT.code -> fsm.sendEvent(ReaderEvents.EOT)
            ReaderEvents.ESC.code -> fsm.sendEvent(ReaderEvents.ESC)
            ReaderEvents.BYTE.code -> fsm.sendEvent(ReaderEvents.BYTE, byte)
            else -> fsm.sendEvent(ReaderEvents.BYTE, byte)
        }
    }
}

class PacketReaderTests {
    @Test
    fun `test reader expect ACK`() {
        val protocolHandler = mockk<ProtocolHandler>()
        every { protocolHandler.sendACK() } just Runs
        val packetReader = Packet(protocolHandler)
        val fsm = PacketReaderFSM(packetReader)
        val stream =
            listOf(
                ReaderEvents.SOH.code,
                ReaderEvents.STX.code,
                'A'.toInt(),
                'B'.toInt(),
                'C'.toInt(),
                ReaderEvents.ETX.code,
                'A'.toInt(),
                ReaderEvents.EOT.code
            )
        stream.forEach { byte ->
            fsm.receiveByte(byte)
        }
        packetReader.print()
        verify { protocolHandler.sendACK() }
        assertTrue { packetReader.checksumValid }
    }

    @Test
    fun `test reader ESC expect ACK`() {
        val protocolHandler = mockk<ProtocolHandler>()
        every { protocolHandler.sendACK() } just Runs
        val packetReader = Packet(protocolHandler)
        val fsm = PacketReaderFSM(packetReader)
        val stream =
            listOf(
                ReaderEvents.SOH.code,
                ReaderEvents.STX.code,
                'A'.toInt(),
                ReaderEvents.ESC.code,
                ReaderEvents.EOT.code,
                'C'.toInt(),
                ReaderEvents.ETX.code,
                'A'.toInt(),
                ReaderEvents.EOT.code
            )
        stream.forEach { byte ->
            fsm.receiveByte(byte)
        }
        packetReader.print()
        verify { protocolHandler.sendACK() }
        assertTrue { packetReader.checksumValid }
        assertTrue { packetReader.fields.size == 1 }
        assertTrue { packetReader.fields[0].size == 3 }
        assertTrue { packetReader.fields[0][1].toInt() == ReaderEvents.EOT.code }
    }

    @Test
    fun `test reader expect NACK`() {
        val protocolHandler = mockk<ProtocolHandler>()
        every { protocolHandler.sendNACK() } just Runs
        val packetReader = Packet(protocolHandler)
        val fsm = PacketReaderFSM(packetReader)
        val stream =
            listOf(
                ReaderEvents.SOH.code,
                ReaderEvents.STX.code,
                'A'.toInt(),
                'B'.toInt(),
                'C'.toInt(),
                ReaderEvents.ETX.code,
                'B'.toInt(),
                ReaderEvents.EOT.code
            )
        stream.forEach { byte ->
            fsm.receiveByte(byte)
        }
        packetReader.print()
        verify { protocolHandler.sendNACK() }
        assertFalse { packetReader.checksumValid }
    }

    @Test
    fun `test reader multiple fields expect ACK`() {
        val protocolHandler = mockk<ProtocolHandler>()
        every { protocolHandler.sendACK() } just Runs
        val packetReader = Packet(protocolHandler)
        val fsm = PacketReaderFSM(packetReader)
        val stream =
            listOf(
                ReaderEvents.SOH.code,
                ReaderEvents.STX.code,
                'A'.toInt(),
                'B'.toInt(),
                'C'.toInt(),
                ReaderEvents.ETX.code,
                ReaderEvents.STX.code,
                'D'.toInt(),
                'E'.toInt(),
                'F'.toInt(),
                ReaderEvents.ETX.code,
                'A'.toInt(),
                'D'.toInt(),
                ReaderEvents.EOT.code
            )
        stream.forEach { byte ->
            fsm.receiveByte(byte)
        }
        packetReader.print()
        verify { protocolHandler.sendACK() }
        assertTrue { packetReader.checksumValid }
        assertTrue { packetReader.fields.size == 2 }
    }
}
