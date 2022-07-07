package application;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import gameException.InvalidIniException;
import util.IniFile;

public class Quiz {

	private final static String INI_FILE = "Quiz.ini";
	private static Scanner sc;
	private static int score;
	private static int dificult;
	private static int maxDificult;
	private static int currentQuestion;
	private static int questionsPerDificultLevel;
	private static Boolean randomQuestionsOrder;
	private static Boolean gameOverOnFirstError;
	private static Map<String, List<String>> questions = new HashMap<>();
	
	private static void iniFileValidation() throws InvalidIniException {
		if (!(new File(INI_FILE)).exists())
			throw new RuntimeException("Arquivo \"" + INI_FILE + "\" não encontrado.");
		IniFile ini = IniFile.getNewIniFileInstance(INI_FILE);
		if (!ini.isSection("CONFIG"))
			throw new InvalidIniException("Não foi possível localizar a sessão [CONFIG]");
		else if(!ini.isItem("CONFIG", "QuestionsPerDificultLevel"))
			throw new InvalidIniException("Não foi possível localizar o parâmetro \"QuestionsPerDificultLevel\" na sessão [CONFIG]");
		else if(!ini.isItem("CONFIG", "RandomQuestionsOrder"))
			throw new InvalidIniException("Não foi possível localizar o parâmetro \"RandomQuestionsOrder\" na sessão [CONFIG]");
		else if(!ini.isItem("CONFIG", "MaxDificult"))
			throw new InvalidIniException("Não foi possível localizar o parâmetro \"MaxDificult\" na sessão [CONFIG]");
		else if(!ini.isItem("CONFIG", "GameOverOnFirstError"))
			throw new InvalidIniException("Não foi possível localizar o parâmetro \"GameOverOnFirstError\" na sessão [CONFIG]");
		questionsPerDificultLevel = Integer.parseInt(ini.read("CONFIG", "QuestionsPerDificultLevel"));
		randomQuestionsOrder = Boolean.parseBoolean(ini.read("CONFIG", "RandomQuestionsOrder"));
		gameOverOnFirstError = Boolean.parseBoolean(ini.read("CONFIG", "GameOverOnFirstError"));
		maxDificult = Integer.parseInt(ini.read("CONFIG", "MaxDificult"));
		questions.clear();
		List<Integer> dificultFound = new ArrayList<>();
		String question;
		for (int dif, cDif = 1; cDif <= maxDificult; cDif++) {
			for (String section : ini.getSectionList())
				if (!section.equals("CONFIG"))
					if (ini.read(section, "Dificult") == null)
						throw new InvalidIniException("Não foi possível localizar o parâmetro \"Dificult\" na sessão [" + section + "]");
					else if (ini.read(section, "Question") == null)
						throw new InvalidIniException("Não foi possível localizar o parâmetro \"Question\" na sessão [" + section + "]");
					else if (ini.read(section, "Answer") == null)
						throw new InvalidIniException("Não foi possível localizar o parâmetro \"Answer\" na sessão [" + section + "]");
					else if (ini.read(section, ini.read(section, "Answer")) == null)
						throw new InvalidIniException("Não foi possível localizar a resposta apontada como correta na sessão [" + section + "]");
					else if (ini.read(section, "1") == null || ini.read(section, "2") == null)
						throw new InvalidIniException("A sessão [" + section + "] deve conter pelo menos duas perguntas válidas.");
					else {
						dif = Integer.parseInt(ini.read(section, "Dificult"));
						if (!dificultFound.contains(dif))
							dificultFound.add(dif);
						if (dif == cDif) {
							question = ini.read(section, "Question");
							questions.put(question, new ArrayList<>());
							for (int n = 1; questions.size() <= questionsPerDificultLevel && ini.read(section, "" + n) != null; n++)
								questions.get(question).add(ini.read(section, ini.getLastReadVal()));
						}
					}
			if (questions.size() < questionsPerDificultLevel)
				throw new InvalidIniException("Não há perguntas de nivel " + cDif + " suficientes. (Foram encontrada(s) " + questions.size() + " pergunta(s), e cada dificuldade deve conter pelo menos " + questionsPerDificultLevel + " perguntas.)");
			questions.clear();
		}
		for (int n = 1; n < maxDificult; n++)
			if (!dificultFound.contains(n))
				throw new InvalidIniException("Não foi encontrada nenhuma pergunta de nivel " + n + ". A sessão [CONFIG] diz que devem ter perguntas até o nivel " + maxDificult);
	}

	private static void loadQuizFromIniFile() throws InvalidIniException {
		IniFile ini = IniFile.getNewIniFileInstance(INI_FILE);
		questions.clear();
		int dif = 1;
		String question;
		for (String section : ini.getSectionList()) {
			if (!section.equals("CONFIG")) {
				dif = Integer.parseInt(ini.read(section, "Dificult"));
				if (dif == dificult) {
					question = ini.read(section, "Question");
					questions.put(question, new ArrayList<>());
					questions.get(question).add(ini.read(section, ini.read(section, "Answer")));
					for (Integer n = 1; questions.size() <= questionsPerDificultLevel && ini.read(section, n.toString()) != null; n++)
						questions.get(question).add(ini.getLastReadVal());
				}
			}
		}
	}

	private static void startQuiz() throws InvalidIniException {
		currentQuestion = 1;
		dificult = 1;
		score = 0;
		Boolean gameOver = false;
		List<Integer> done = new ArrayList<>();
		int q = 1;
		String question;
		int choice = 0;
		while (!gameOver) {
			loadQuizFromIniFile();
			System.out.println("Prepare-se para as perguntas de nivel " + dificult + ":\n");
			while (!gameOver && done.size() < questions.size()) {
				while (randomQuestionsOrder && done.contains(q = new SecureRandom().nextInt(questions.size())));
				question = questions.keySet().stream().collect(Collectors.toList()).get(q);
				System.out.println("Pergunta " + currentQuestion + ":\n");
				System.out.println(question + "\n");
				for (int n = 1; n < questions.get(question).size(); n++) 
					System.out.println(n + ") " + questions.get(question).get(n));
				done.add(q++);
				System.out.println();
				while (true) {
					try {
						choice = Integer.parseInt(sc.nextLine());
						currentQuestion++;
					}
					catch (NumberFormatException e) {
						System.out.println("Opção inválida!");
					}
					if (choice == 0 || choice > questions.get(question).size())
						System.out.println("Opção inválida!");
					else
						break;
				}
				if (questions.get(question).get(0).equals(questions.get(question).get(choice))) {
					System.out.println("Certa a resposta!\n");
					score++;
				}
				else if (!gameOverOnFirstError)
					System.out.println("A resposta está incorreta!\n");
				else {
					System.out.println("A resposta está incorreta!\n");
					gameOver = true;
				}
			}
			if (++dificult > maxDificult)
				gameOver = true;
		}
		System.out.println("Fim de jogo!");
		System.out.printf("Você acertou %d de %d perguntas\n", score, maxDificult * questionsPerDificultLevel);
	}

	public static void main(String[] argsv) throws InvalidIniException {
		iniFileValidation();
		sc = new Scanner(System.in);
		while (true) {
			startQuiz();
			System.out.print("\nDeseja jogar outra partida? (s|n): ");
			if (sc.nextLine().toLowerCase().charAt(0) != 's')
				break;
			else
				System.out.println("\nOk! Reiniciando jogo...\n");
		}
		System.out.println("\nAté a próxima!\n");
		sc.close();
	}

}
