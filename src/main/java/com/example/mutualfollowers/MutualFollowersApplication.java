package com.example.mutualfollowers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class MutualFollowersApplication {

	public static void main(String[] args) {
		SpringApplication.run(MutualFollowersApplication.class, args);
	}

	@PostConstruct
	public void runOnStartup() throws Exception {
		RestTemplate restTemplate = new RestTemplate();
		ObjectMapper mapper = new ObjectMapper();

		String registerUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("name", "Mohammad Liyakat Ali");
		requestBody.put("regNo", "RA2211003020725");
		requestBody.put("email", "ma8107@srmist.edu.in");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

		ResponseEntity<GenerateWebhookResponse> response = restTemplate.exchange(
				registerUrl, HttpMethod.POST, entity, GenerateWebhookResponse.class);

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			GenerateWebhookResponse body = response.getBody();
			List<List<Integer>> outcome = solveMutualFollowers(body.getData().getUsers());

			// Step 2: POST result to webhook
			Map<String, Object> output = new HashMap<>();
			output.put("regNo", "RA2211003020725");
			output.put("outcome", outcome);

			HttpHeaders webhookHeaders = new HttpHeaders();
			webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
			webhookHeaders.set("Authorization", body.getAccessToken());

			HttpEntity<Map<String, Object>> resultEntity = new HttpEntity<>(output, webhookHeaders);
			int retries = 0;
			boolean success = false;
			while (retries < 4 && !success) {
				try {
					ResponseEntity<String> webhookResponse = restTemplate.postForEntity(
							body.getWebhook(), resultEntity, String.class);
					success = webhookResponse.getStatusCode().is2xxSuccessful();
				} catch (Exception e) {
					retries++;
				}
			}
		}
	}

	private List<List<Integer>> solveMutualFollowers(List<User> users) {
		Map<Integer, Set<Integer>> followMap = new HashMap<>();
		for (User user : users) {
			followMap.put(user.getId(), new HashSet<>(user.getFollows()));
		}

		Set<String> seen = new HashSet<>();
		List<List<Integer>> result = new ArrayList<>();

		for (User user : users) {
			for (Integer followedId : user.getFollows()) {
				if (followMap.containsKey(followedId) && followMap.get(followedId).contains(user.getId())) {
					int a = Math.min(user.getId(), followedId);
					int b = Math.max(user.getId(), followedId);
					String key = a + "," + b;
					if (!seen.contains(key)) {
						result.add(Arrays.asList(a, b));
						seen.add(key);
					}
				}
			}
		}

		return result;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class GenerateWebhookResponse {
		@JsonProperty("webhook")
		private String webhook;

		@JsonProperty("accessToken")
		private String accessToken;

		@JsonProperty("data")
		private DataWrapper data;

		public String getWebhook() { return webhook; }
		public String getAccessToken() { return accessToken; }
		public DataWrapper getData() { return data; }
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class DataWrapper {
		@JsonProperty("users")
		private List<User> users;

		public List<User> getUsers() { return users; }
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class User {
		@JsonProperty("id")
		private int id;

		@JsonProperty("name")
		private String name;

		@JsonProperty("follows")
		private List<Integer> follows;

		public int getId() { return id; }
		public String getName() { return name; }
		public List<Integer> getFollows() { return follows; }
	}
}