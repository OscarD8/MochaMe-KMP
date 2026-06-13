package com.mochame.contract.metadata


sealed class MochaModule {
    abstract val id: Int
    abstract val moduleName: String
    abstract val modelName: String

    sealed class Bio : MochaModule() {
        override val id = 1
        override val moduleName: String = "BIO"
        data object DailyContext : Bio() {
            override val modelName = "DAILYCONTEXT"
        }
    }

    sealed class Telemetry : MochaModule() {
        override val id = 2
        override val moduleName: String = "TELEMETRY"
        data object Topic  : Telemetry() { override val modelName = "TOPIC"   }
        data object Domain : Telemetry() { override val modelName = "DOMAIN"  }
        data object Moment : Telemetry() { override val modelName = "MOMENT"  }
    }

    sealed class Resonance : MochaModule() {
        override val id = 3
        override val moduleName: String = "RESONANCE"
        data object Book   : Resonance() { override val modelName = "BOOK"    }
        data object Author : Resonance() { override val modelName = "AUTHOR"  }
        data object Quote  : Resonance() { override val modelName = "QUOTE"   }
    }

    companion object {
        val all: List<MochaModule> = listOf(
            Bio.DailyContext,
            Telemetry.Topic, Telemetry.Domain, Telemetry.Moment,
            Resonance.Book, Resonance.Author, Resonance.Quote
        )

        fun modelFromString(model: String): MochaModule =
            all.first { it.modelName == model }

        fun moduleFromString(module: String): MochaModule =
            all.first { it.moduleName == module }

        fun fromId(id: Int): MochaModule =
            all.first { it.id == id }
    }
}