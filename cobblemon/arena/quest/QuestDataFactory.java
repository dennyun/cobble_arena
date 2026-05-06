package cobblemon.arena.quest;

import java.util.List;

public class QuestDataFactory {

    public static List<Quest> getDailyQuests() {
        return List.of(
            new Quest("daily_01", QuestType.PLAY_MATCHES, "Primeiro Passo", "Participe de 1 partida em qualquer fila.", 1, null, new QuestReward("50 Battle Point", List.of("eco deposit 50 battlepoint %player%")), true),
            new Quest("daily_02", QuestType.WIN_RANKED, "Espírito de Luta", "Vença 1 partida Ranqueada.", 1, null, new QuestReward("100 Battle Point", List.of("eco deposit 100 battlepoint %player%")), true),
            new Quest("daily_03", QuestType.PLAY_CASUAL, "Treino Casual", "Jogue 2 partidas na fila Casual.", 2, null, new QuestReward("60 Battle Point", List.of("eco deposit 60 battlepoint %player%")), true),
            new Quest("daily_04", QuestType.PLAY_NO_FORFEIT, "Resiliência", "Complete 3 partidas sem desistir (Forfeit).", 3, null, new QuestReward("80 Battle Point", List.of("eco deposit 80 battlepoint %player%")), true),
            new Quest("daily_05", QuestType.PLAY_MATCHES, "Duelo de Duplas", "Participe de 2 batalhas no formato Doubles.", 2, "doubles", new QuestReward("75 Battle Point", List.of("eco deposit 75 battlepoint %player%")), true),
            new Quest("daily_06", QuestType.USE_TYPE, "Calor da Batalha", "Jogue uma partida usando ao menos um Pokémon do tipo Fogo.", 1, "fire", new QuestReward("60 Battle Point", List.of("eco deposit 60 battlepoint %player%")), true),
            new Quest("daily_07", QuestType.USE_TYPE, "Choque do Trovão", "Jogue 2 partidas usando ao menos um Pokémon do tipo Elétrico.", 2, "electric", new QuestReward("70 Battle Point", List.of("eco deposit 70 battlepoint %player%")), true),
            new Quest("daily_08", QuestType.USE_TYPE, "Mestre das Sombras", "Jogue uma partida usando um Pokémon do tipo Fantasma.", 1, "ghost", new QuestReward("60 Battle Point", List.of("eco deposit 60 battlepoint %player%")), true),
            new Quest("daily_09", QuestType.USE_TYPE, "Força Bruta", "Jogue uma partida usando um Pokémon tipo Lutador.", 1, "fighting", new QuestReward("60 Battle Point", List.of("eco deposit 60 battlepoint %player%")), true),
            new Quest("daily_10", QuestType.USE_TYPE, "Raízes Fortes", "Jogue uma partida usando um Pokémon tipo Planta.", 1, "grass", new QuestReward("60 Battle Point", List.of("eco deposit 60 battlepoint %player%")), true),
            new Quest("daily_11", QuestType.WIN_MATCHES, "Finalizador", "Vença 1 partida focando em manter seus Pokémon vivos.", 1, null, new QuestReward("90 Battle Point", List.of("eco deposit 90 battlepoint %player%")), true),
            new Quest("daily_12", QuestType.WIN_MATCHES, "Contra-Ataque", "Vença 1 partida focando em estratégias de virada.", 1, null, new QuestReward("90 Battle Point", List.of("eco deposit 90 battlepoint %player%")), true),
            new Quest("daily_13", QuestType.PLAY_LONG, "Estrategista", "Jogue uma partida longa (mais de 20 turnos).", 20, null, new QuestReward("100 Battle Point", List.of("eco deposit 100 battlepoint %player%")), true),
            new Quest("daily_14", QuestType.WIN_FAST, "Velocista", "Vença uma partida em 10 turnos ou menos.", 10, null, new QuestReward("120 Battle Point", List.of("eco deposit 120 battlepoint %player%")), true),
            new Quest("daily_15", QuestType.WIN_MATCHES, "Inabalável", "Vença 1 partida resistindo a ataques inimigos.", 1, null, new QuestReward("90 Battle Point", List.of("eco deposit 90 battlepoint %player%")), true),
            new Quest("daily_16", QuestType.PLAY_MATCHES, "Mestre de Singles", "Jogue 2 partidas no formato Singles.", 2, "singles", new QuestReward("75 Battle Point", List.of("eco deposit 75 battlepoint %player%")), true),
            new Quest("daily_17", QuestType.PLAY_CASUAL, "Novos Ares", "Jogue uma partida Casual testando estratégias.", 1, null, new QuestReward("50 Battle Point", List.of("eco deposit 50 battlepoint %player%")), true),
            new Quest("daily_18", QuestType.WIN_STREAK, "Colecionador de Vitórias", "Alcance uma sequência (streak) de 2 vitórias seguidas.", 2, null, new QuestReward("150 Battle Point", List.of("eco deposit 150 battlepoint %player%")), true),
            new Quest("daily_19", QuestType.PLAY_LONG, "Paciência de Ló", "Complete uma partida que dure mais de 20 turnos.", 20, null, new QuestReward("80 Battle Point", List.of("eco deposit 80 battlepoint %player%")), true),
            new Quest("daily_20", QuestType.PLAY_LONG, "Apoio Técnico", "Sobreviva a 15 turnos focando em movimentos de status.", 15, null, new QuestReward("80 Battle Point", List.of("eco deposit 80 battlepoint %player%")), true)
        );
    }

    public static List<Quest> getWeeklyQuests() {
        return List.of(
            new Quest("weekly_21", QuestType.WIN_RANKED, "Veterano de Arena", "Vença 15 partidas Ranqueadas.", 15, null, new QuestReward("1000 Battle Point", List.of("eco deposit 1000 battlepoint %player%")), false),
            new Quest("weekly_22", QuestType.WIN_RANKED, "Escalada de Elite", "Vença 5 partidas Ranqueadas focando em subir de ELO.", 5, null, new QuestReward("800 Battle Point", List.of("eco deposit 800 battlepoint %player%")), false),
            new Quest("weekly_23", QuestType.WIN_RANKED, "Dominador de Doubles", "Vença 10 partidas no formato Doubles Ranqueado.", 10, "doubles", new QuestReward("900 Battle Point", List.of("eco deposit 900 battlepoint %player%")), false),
            new Quest("weekly_24", QuestType.WIN_RANKED, "Top de Singles", "Vença 10 partidas no formato Singles Ranqueado.", 10, "singles", new QuestReward("900 Battle Point", List.of("eco deposit 900 battlepoint %player%")), false),
            new Quest("weekly_25", QuestType.WIN_STREAK, "Invencível", "Consiga uma sequência de 5 vitórias seguidas.", 5, null, new QuestReward("1500 Battle Point", List.of("eco deposit 1500 battlepoint %player%")), false),
            new Quest("weekly_26", QuestType.PLAY_RANKED, "Constância", "Jogue pelo menos 5 partidas Ranqueadas.", 5, null, new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_27", QuestType.PLAY_CASUAL, "Testador de Meta", "Jogue 20 partidas na fila Casual.", 20, null, new QuestReward("800 Battle Point", List.of("eco deposit 800 battlepoint %player%")), false),
            new Quest("weekly_28", QuestType.PLAY_MONOTYPE, "Mestre Monotype", "Participe de 5 partidas usando um time Monotype (mesmo tipo).", 5, null, new QuestReward("700 Battle Point", List.of("eco deposit 700 battlepoint %player%")), false),
            new Quest("weekly_29", QuestType.PLAY_MATCHES, "Multi-Formato", "Jogue 15 partidas (qualquer formato).", 15, null, new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_30", QuestType.KNOCKOUT_TOTAL, "Professor Pokémon", "Nocauteie 50 Pokémon adversários no total.", 50, null, new QuestReward("1200 Battle Point", List.of("eco deposit 1200 battlepoint %player%")), false),
            new Quest("weekly_31", QuestType.PLAY_NO_FORFEIT, "Estrategista VGC", "Jogue 10 partidas sem desistir.", 10, null, new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_32", QuestType.WIN_MATCHES, "Barreira Intransponível", "Vença 5 partidas focando em não perder Pokémon.", 5, null, new QuestReward("800 Battle Point", List.of("eco deposit 800 battlepoint %player%")), false),
            new Quest("weekly_33", QuestType.PLAY_LONG, "Mestre das Trocas", "Jogue 5 partidas longas (mais de 10 turnos cada).", 10, null, new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_34", QuestType.KNOCKOUT_TOTAL, "Crítico", "Realize 10 nocautes agressivos na semana.", 10, null, new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_35", QuestType.USE_TYPE, "Clima Favorável", "Jogue 5 partidas utilizando estratégias de clima (ex: tipo Água).", 5, "water", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_36", QuestType.USE_TYPE, "Caminho do Dragão", "Jogue 5 partidas usando um Pokémon tipo Dragão.", 5, "dragon", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_37", QuestType.USE_TYPE, "Mente Brilhante", "Jogue 5 partidas usando um Pokémon tipo Psíquico.", 5, "psychic", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_38", QuestType.USE_TYPE, "Armadura de Aço", "Jogue 5 partidas usando um Pokémon tipo Aço.", 5, "steel", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_39", QuestType.USE_TYPE, "Terror dos Mares", "Jogue 5 partidas usando um Pokémon tipo Água.", 5, "water", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_40", QuestType.USE_TYPE, "Peso Pesado", "Jogue 5 partidas usando um Pokémon tipo Terrestre.", 5, "ground", new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_41", QuestType.PLAY_LONG, "Maratonista", "Acumule 100 turnos jogados na Arena.", 100, null, new QuestReward("1000 Battle Point", List.of("eco deposit 1000 battlepoint %player%")), false),
            new Quest("weekly_42", QuestType.KNOCKOUT_TOTAL, "Dano Massivo", "Acumule 30 nocautes de Pokémon adversários.", 30, null, new QuestReward("1000 Battle Point", List.of("eco deposit 1000 battlepoint %player%")), false),
            new Quest("weekly_43", QuestType.KNOCKOUT_TOTAL, "Vingador", "Acumule 15 nocautes contra os MVP do adversário.", 15, null, new QuestReward("700 Battle Point", List.of("eco deposit 700 battlepoint %player%")), false),
            new Quest("weekly_44", QuestType.PLAY_LONG, "Mestre dos Itens", "Acumule 20 turnos usando táticas de Leftovers/Itens.", 20, null, new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_45", QuestType.WIN_FAST, "Especialista em Leads", "Vença 5 partidas rapidamente (em menos de 5 turnos).", 5, null, new QuestReward("900 Battle Point", List.of("eco deposit 900 battlepoint %player%")), false),
            new Quest("weekly_46", QuestType.PLAY_MATCHES, "Triplo Combo", "Participe de 5 batalhas no formato Triples.", 5, "triples", new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_47", QuestType.WIN_BY_FORFEIT, "Sem Piedade", "Vença uma partida em que o adversário deu Forfeit.", 1, null, new QuestReward("400 Battle Point", List.of("eco deposit 400 battlepoint %player%")), false),
            new Quest("weekly_48", QuestType.WIN_MATCHES, "Virada Histórica", "Vença 1 partida no sufoco.", 1, null, new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_49", QuestType.PLAY_MATCHES, "Puro Talento", "Jogue 5 partidas usando Pokémon táticos.", 5, null, new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_50", QuestType.PLAY_LONG, "Engenheiro de Stats", "Acumule 15 turnos buffando atributos.", 15, null, new QuestReward("500 Battle Point", List.of("eco deposit 500 battlepoint %player%")), false),
            new Quest("weekly_51", QuestType.PLAY_MATCHES, "Item Clause Pro", "Jogue 10 partidas usando itens variados no time.", 10, null, new QuestReward("700 Battle Point", List.of("eco deposit 700 battlepoint %player%")), false),
            new Quest("weekly_52", QuestType.PLAY_MATCHES, "Species Clause Pro", "Jogue 15 partidas seguindo regras estritas.", 15, null, new QuestReward("800 Battle Point", List.of("eco deposit 800 battlepoint %player%")), false),
            new Quest("weekly_53", QuestType.PLAY_CASUAL, "Nível 50", "Jogue 10 partidas focadas no formato Nível 50 Casual.", 10, null, new QuestReward("700 Battle Point", List.of("eco deposit 700 battlepoint %player%")), false),
            new Quest("weekly_54", QuestType.WIN_FAST, "Resistência Elemental", "Vença 5 partidas de forma segura e rápida.", 5, null, new QuestReward("800 Battle Point", List.of("eco deposit 800 battlepoint %player%")), false),
            new Quest("weekly_55", QuestType.PLAY_LONG, "Predição", "Acumule 10 turnos usando Protect perfeitamente.", 10, null, new QuestReward("600 Battle Point", List.of("eco deposit 600 battlepoint %player%")), false),
            new Quest("weekly_56", QuestType.PLAY_CASUAL, "Espectador", "Assista ou participe de 5 partidas amigáveis.", 5, null, new QuestReward("400 Battle Point", List.of("eco deposit 400 battlepoint %player%")), false),
            new Quest("weekly_57", QuestType.PLAY_MATCHES, "Desafiante Frequente", "Complete 50 partidas totais na Arena.", 50, null, new QuestReward("2000 Battle Point", List.of("eco deposit 2000 battlepoint %player%")), false),
            new Quest("weekly_58", QuestType.WIN_RANKED, "Carrasco de Lendas", "Vença 5 partidas Ranqueadas lendárias.", 5, null, new QuestReward("1000 Battle Point", List.of("eco deposit 1000 battlepoint %player%")), false),
            new Quest("weekly_59", QuestType.PLAY_CASUAL, "Duelo Amigável", "Participe de 1 partida Casual Amigável e vença.", 1, null, new QuestReward("300 Battle Point", List.of("eco deposit 300 battlepoint %player%")), false),
            new Quest("weekly_60", QuestType.WIN_RANKED, "Lenda da Arena", "Alcance glória vencendo 20 ranqueadas na semana.", 20, null, new QuestReward("2500 Battle Point", List.of("eco deposit 2500 battlepoint %player%")), false)
        );
    }
}
