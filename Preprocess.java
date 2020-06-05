import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Preprocess {
	private String dataset; // number of test runs
	private int run_num; // number of test runs
	private int golden_num; // number of golden tasks
	private double mu; // percentage of Sybil workers
	private int lambda; // number of attackers
	private double epsilon; // probability for Sybil workers to deviate from sharing 
	private int N; // number of tasks
	private int M; // number of workers
	private int L; // optional label size
	private int K; // number of workers per normal task
	private double theta; // average worker accuracy
	private boolean has_golden; // indicate whether golden tasks are provided
	
	private Map<Integer, Map<Integer, Integer>> worker_normal_labels = new HashMap<Integer, Map<Integer, Integer>>(); // (task, label) pairs of each worker on normal tasks
	private Map<Integer, ArrayList<Integer>> normal_workers = new HashMap<Integer, ArrayList<Integer>>(); // workers on each normal task
	private Map<Integer, Integer> normal_truth = new HashMap<Integer, Integer>(); // true label of each normal task
	private Map<Integer, Map<Integer, Integer>> worker_golden_labels = new HashMap<Integer, Map<Integer, Integer>>(); // (golden task, label) pairs of each worker on golden tasks
	private Map<Integer, Integer> golden_truth = new HashMap<Integer, Integer>(); // true label of each golden task
	private Map<Integer, ArrayList<Integer>> attacker_sybils = new HashMap<Integer, ArrayList<Integer>>(); // Sybil workers of each attacker
	private Map<String, Integer> worker_id = new HashMap<String, Integer>(); // worker to id mapping
	
	/* initialization for real datasets with golden tasks (NLP) */
	public Preprocess(String dataset, int run_num, double mu, double epsilon, int lambda) {
		this.dataset = dataset;
		this.run_num = run_num;
		this.mu = mu;
		this.epsilon = epsilon;
		this.lambda = lambda;
		has_golden = true;
		readData();
	}
	
	/* initialization for real datasets without golden tasks (DOG) */
	public Preprocess(String dataset, int run_num, double mu, double epsilon, int lambda, int golden_num) {
		this.dataset = dataset;
		this.run_num = run_num;
		this.mu = mu;
		this.epsilon = epsilon;
		this.lambda = lambda;
		this.golden_num = golden_num;
		has_golden = false;
		readData();
	}
	
	/* initialization for synthetic datasets (SYN) */
	public Preprocess(String dataset, int run_num, double mu, double epsilon, int lambda, int golden_num, int N, int M, int L, int K, double theta) {
		this.dataset = dataset;
		this.run_num = run_num;
		this.mu = mu;
		this.epsilon = epsilon;
		this.lambda = lambda;
		this.golden_num = golden_num;
		has_golden = false;
		this.N = N;
		this.M = M;
		this.L = L;
		this.K = K;
		this.theta = theta;
		simulate();
	}
	
	/* read (task, worker, label) tuples of normal tasks in answer.csv,
	 * read (task, true label) pairs of normal tasks in truth.csv,
	 * read (task, worker, label) tuples of golden tasks in quali.csv 
	 * and read (task, true label) pairs of golden tasks in quali_truth.csv */
	public void readData() {
		try {
			BufferedReader r1 = new BufferedReader(new FileReader(dataset+"\\answer.csv"));
			String line = r1.readLine();
			line = r1.readLine();
			int id = 0;
			L = 0;
			K = 0;
			while(line!=null) {
				String[] elements = line.split(",");
				int task = Integer.parseInt(elements[0]);
				// map string ID to integer ID
				int worker = id;
				if(worker_id.containsKey(elements[1])) {
					worker = worker_id.get(elements[1]);
				}
				else {
					worker_id.put(elements[1], worker);
					id++;
				}
				int label = Integer.parseInt(elements[2]);
				if(label+1>L) {
					L = label + 1;
				}
				
				// update worker_normal_labels
				Map<Integer, Integer> normal_labels = worker_normal_labels.get(worker);
				if(normal_labels==null) {
					normal_labels = new HashMap<Integer, Integer>();
				}
				normal_labels.put(task, label);
				worker_normal_labels.put(worker, normal_labels);
				
				// update normal_workers
				ArrayList<Integer> workers = normal_workers.get(task);
				if(workers==null) {
					workers = new ArrayList<Integer>();
				}
				workers.add(worker);
				if(workers.size()>K) {
					K = workers.size();
				}
				normal_workers.put(task, workers);

				line = r1.readLine();
			}
			N = normal_workers.size();
			M = worker_id.size();
			r1.close();
			
			
			BufferedReader r2 = new BufferedReader(new FileReader(dataset+"\\truth.csv"));
			line = r2.readLine();
			line = r2.readLine();
			while(line!=null) {
				String[] elements = line.split(",");
				int task = Integer.parseInt(elements[0]);
				int truth = Integer.parseInt(elements[1]);
				
				// update normal_truth
				normal_truth.put(task, truth);
				
				line = r2.readLine();
			}
			r2.close();
			
			if(has_golden) {
				BufferedReader r3 = new BufferedReader(new FileReader(dataset+"\\quali.csv"));
				line = r3.readLine();
				line = r3.readLine();
				while(line!=null) {
					String[] elements = line.split(",");
					int task = Integer.parseInt(elements[0]);
					int worker = worker_id.get(elements[1]);
					int label = Integer.parseInt(elements[2]);
					
					// update worker_golden_labels
					Map<Integer, Integer> golden_labels = worker_golden_labels.get(worker);
					if(golden_labels==null) {
						golden_labels = new HashMap<Integer, Integer>();
					}
					golden_labels.put(task, label);
					worker_golden_labels.put(worker, golden_labels);
					line = r3.readLine();
				}
				r3.close();
				
				BufferedReader r4 = new BufferedReader(new FileReader(dataset+"\\quali_truth.csv"));
				line = r4.readLine();
				line = r4.readLine();
				while(line!=null) {
					String[] elements = line.split(",");
					int task = Integer.parseInt(elements[0]);
					int truth = Integer.parseInt(elements[1]);
					
					// update golden_truth
					golden_truth.put(task, truth);
					
					line = r4.readLine();
				}
				golden_num = golden_truth.size();
				r4.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void simulate() {
		// create tasks and their true label
		Random rand = new Random();
		for(int i=0; i<N; i++) {
			normal_truth.put(i, rand.nextInt(L));
		}
		
		// create workers and their label on tasks
		ArrayList<Integer> avail_workers = new ArrayList<Integer>();
		for(int i=0; i<M; i++) {
			avail_workers.add(i);
			worker_normal_labels.put(i, new HashMap<Integer, Integer>());
		}
		int answer_num = N*K/M+5;
		ArrayList<Integer> removed = new ArrayList<Integer>();
		for(int i=0; i<N; i++) {
			Collections.shuffle(avail_workers);
			ArrayList<Integer> t_workers = new ArrayList<Integer>();
			for(int j=0; j<K; j++) {
				int worker = avail_workers.get(avail_workers.size()-1-j);
				Map<Integer, Integer> task_labels = worker_normal_labels.get(worker);
				int truth = normal_truth.get(i);
				if(rand.nextDouble()<=theta) {
					task_labels.put(i, truth);
				}
				else {
					int label = rand.nextInt(L);
					while(label==truth) {
						label = rand.nextInt(L);
					}
					task_labels.put(i, label);
				}
				worker_normal_labels.put(worker, task_labels);
				if(task_labels.size()==answer_num) {
					removed.add(worker);
				}
				t_workers.add(worker);
			}
			avail_workers = new ArrayList<Integer>();
			for(int j=0; j<M; j++) {
				if(!removed.contains(j)) {
					avail_workers.add(j);
				}
			}
			normal_workers.put(i, t_workers);
		}
	}
	
	/* replace mu percentage of independent normal workers with Sybil workers
	 * and equally assign Sybil workers to lambda attackers */
	public void replace() {
		attacker_sybils = new HashMap<Integer, ArrayList<Integer>>();
		// decide the number of Sybil workers for each attacker
		int num = (int) Math.ceil(worker_normal_labels.size()*mu);
		int[] attacker_Sybil_num = new int[lambda];
		for(int i=0; i<lambda; i++) {
			attacker_Sybil_num[i] = (int) Math.floor(num/lambda);
		}
		attacker_Sybil_num[lambda-1] = num - attacker_Sybil_num[0]*(lambda-1);
		
		// update attacker_sybils by randomly assigning independent workers to each attacker as Sybil workers
		ArrayList<Integer> temp_workers = new ArrayList<Integer>(worker_normal_labels.keySet());
		Collections.shuffle(temp_workers);
		for(int i=0; i<lambda; i++) {
			ArrayList<Integer> workers = new ArrayList<Integer>();
			for(int j=0; j<attacker_Sybil_num[i]; j++) {
				Integer worker = temp_workers.remove(temp_workers.size()-1);
				workers.add(worker);
			}
			attacker_sybils.put(i, workers);
		}
	}
	
	/* write the overall data info into input.txt and write the information 
	 * of Sybil attack, golden tasks and request order for each run */
	public void formalize() {
		Random rand = new Random();
		try{
			BufferedWriter w1 = new BufferedWriter(new FileWriter(dataset+"\\input.txt"));
			// write worker number M, task number N, label size L and worker number per task K
			w1.write(M+"\t"+N+"\t"+L+"\t"+K+"\n");
			for(Integer task : normal_truth.keySet()) {
				// write task ID, true label and number of workers for each task  
				w1.write(task+"\t"+normal_truth.get(task)+"\t"+normal_workers.get(task).size()+"\t");
				// write worker ID and corresponding label
				for(Integer worker : normal_workers.get(task)) {
					w1.write(worker.intValue()+"\t"+worker_normal_labels.get(worker).get(task).intValue()+"\t");
				}
				w1.write("\n");
			}
			w1.close();
			
			for(int run=0; run<run_num; run++) {
				File f = new File(dataset+"/"+run);
				f.mkdir();
				replace();
				
				// attack.txt contains the labels randomized by each attacker and the Sybil workers controlled by each attacker
				BufferedWriter w2 = new BufferedWriter(new FileWriter(dataset+"\\"+run+"\\attack.txt"));
				w2.write(mu+"\t"+epsilon+"\t"+lambda+"\n");
				for(int i=0; i<lambda; i++) {
					// write attacker ID and total number of tasks for each attacker
					w2.write(i+"\t"+(normal_truth.size()+golden_num)+"\t");
					// write task ID and randomized label for normal tasks
					for(Integer task : normal_truth.keySet()) {
						w2.write(task.intValue()+"\t"+rand.nextInt(L)+"\t");
					}
					// write task ID and randomized label for golden tasks
					if(has_golden) {
						for(Integer golden : golden_truth.keySet()) {
							w2.write(golden.intValue()+"\t"+rand.nextInt(L)+"\t");
						}
						w2.write("\n");
					}
					else {
						for(int j=-1; j>=0-golden_num; j--) {
							w2.write(j+"\t"+rand.nextInt(L)+"\t");
						}
						w2.write("\n");
					}
					ArrayList<Integer> workers = attacker_sybils.get(i);
					// write attacker ID and number of Sybil workers for each attacker
					w2.write(i+"\t"+workers.size()+"\t");
					// write worker ID of replaced independent workers
					for(Integer worker : workers) {
						w2.write(worker.intValue()+"\t");
					}
					w2.write("\n");
				}
				w2.close();
				
				BufferedWriter w3 = new BufferedWriter(new FileWriter(dataset+"\\"+run+"\\golden.txt"));
				w3.write(golden_num+"\n");
				if(has_golden) {
					// write the true label of each golden task
					for(Integer golden : golden_truth.keySet()) {
						w3.write(golden.intValue()+"\t"+golden_truth.get(golden).intValue()+"\t");
					}
					w3.write("\n");
					// write the label of each worker on each golden task
					for(Integer worker : worker_golden_labels.keySet()) {
						w3.write(worker.intValue()+"\t");
						Map<Integer, Integer> golden_labels = worker_golden_labels.get(worker);
						for(Integer golden : golden_labels.keySet()) {
							w3.write(golden.intValue()+"\t"+golden_labels.get(golden).intValue()+"\t");
						}
						w3.write("\n");
					}
				}
				else {
					// generate golden tasks
					int[] golden_labels = new int[golden_num];
					for(int i=0; i<golden_num; i++) {
						golden_labels[i] = rand.nextInt(L);
						w3.write((-1-i)+"\t"+golden_labels[i]+"\t");
					}
					w3.write("\n");
					// determine the label provided by each worker on golden tasks
					for(Integer worker : worker_normal_labels.keySet()) {
						w3.write(worker.intValue()+"\t");
						// compute the worker's accuracy
						double acc = 0.0;
						Map<Integer, Integer> task_labels = worker_normal_labels.get(worker);				
						for(Integer task : task_labels.keySet()) {
							if(normal_truth.get(task).intValue()==task_labels.get(task).intValue()) {
								acc += 1;
							}
						}
						acc = acc/task_labels.size();
						
						// generate the worker's label on each golden task based on the computed accuracy
						for(int i=0; i<golden_num; i++) {
							w3.write(-1-i+"\t");
							if(rand.nextDouble()<=acc) {
								w3.write(golden_labels[i]+"\t");
							}
							else {
								int answer = rand.nextInt(L);
								while(answer==golden_labels[i]) {
									answer = rand.nextInt(L);
								}
								w3.write(answer+"\t");
							}
						}
						w3.write("\n");
					}
				}
				w3.close();
				
				BufferedWriter w4 = new BufferedWriter(new FileWriter(dataset+"\\"+run+"\\order.txt"));
				ArrayList<Integer> order = new ArrayList<Integer>();
				// decide the number of requests for each worker
				for(Integer worker : worker_normal_labels.keySet()) {
					Map<Integer, Integer> task_labels = worker_normal_labels.get(worker);
					for(int i=0; i<task_labels.size(); i++) {
						order.add(worker);
					}
					for(int i=0; i<golden_num; i++) {
						order.add(worker);
					}
				}
				// randomize the request order
				Collections.shuffle(order);
				for(Integer worker : order) {
					w4.write(worker.intValue()+"\n");
				}
				w4.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/* main function */
	public static void main(String[] args) {
		if(args.length!=5 && args.length!=6 && args.length!=11) {
			System.out.println("Invalid parameter length!");
			System.exit(0);
		}
		String dataset = args[0];
		int run_num = Integer.parseInt(args[1]);
		double mu = Double.parseDouble(args[2]);
		double epsilon = Double.parseDouble(args[3]);
		int lambda = Integer.parseInt(args[4]);
		
		Preprocess pre;
		if(args.length==5) {
			pre = new Preprocess(dataset, run_num, mu, epsilon, lambda);
		}
		else {
			int golden_num = Integer.parseInt(args[5]);
			if(args.length==6) {
				pre = new Preprocess(dataset, run_num, mu, epsilon, lambda, golden_num);
			}
			else {
				int N = Integer.parseInt(args[6]);
				int M = Integer.parseInt(args[7]);
				int L = Integer.parseInt(args[8]);
				int K = Integer.parseInt(args[9]);
				double theta = Double.parseDouble(args[10]);
				pre = new Preprocess(dataset, run_num, mu, epsilon, lambda, golden_num, N, M, L, K, theta);
			}
		}
		
		if(pre!=null) {
			pre.formalize();
			System.out.println("The dataset " + dataset +" is ready for testing!");
		}
	}
}