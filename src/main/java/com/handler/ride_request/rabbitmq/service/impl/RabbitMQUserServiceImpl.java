package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RabbitMQUserServiceImpl implements RabbitMQUserService {

    private final AmqpAdmin amqpAdmin;

    private final DirectExchange userExchange;

    // Create and bind a queue for the user
    @Override
    public void createUserQueue(String userId) {
        String queueName = "queue.user." + userId;

        // Declare a new queue
        Queue userQueue = new Queue(queueName, true);
        amqpAdmin.declareQueue(userQueue);

        // Bind the queue to the exchange with the user's identifier as the routing key
        Binding binding = BindingBuilder.bind(userQueue)
                .to(userExchange)
                .with(userId);
        amqpAdmin.declareBinding(binding);
    }
}

//TODO:
//Mes vacances en Tunisie.
//Docker.

//Les 4 jours 9bal menarja3 lel 5edma.
//Les tests unitaires.
//Test d'intégration.
//Jenkins.
//Article Kafka vs Rabbitmq.

//Janvier => Avril.
//Reste du projet: How to respond randomly to driver notification to accept or decline a ride.
//Microservices.
//Article technique sur le projet.
//Article Jenkins
//Article Docker.

//Préparation certification kubernetes: Avril => Aout.

//Nlem 6000 euros: Janvier => Avril. (700/mois)*3mois.
//Nlem 8000 euros: Avril => Novembre. (300/mois)*7mois.
//Voyage: 1500: (214/mois)*7mois.
//Tounis: 2000 euros => (250/mois)*12 mois.
//Nlem 10000 euros: Novembre => Avril 2026. (400/mois)*5mois.
//Narja3 lel sport: jeudi, vendredi, samedi et dimanche.
//Dimanche: batch cooking.
//Samedi: na9dhi sbe7 bekri.
//Samedi 3chiya: activité.
//Dimanche sbe7: Walk in the nature sbé7 bekri + sport.
//3chiya ba3d 5edma: Lundi 3chiya et vendredi 3chiya: nemchi lel 9ahwa wnerkech ne5dem wela na9ra kteb.
//Mai: n9os merwe7 lel tounis: 2 semaines.
//Aout: voyage pour 4 jours.
//Septembre: merwe7 lel tounis pour les 2 premieres semaines du mois du Septembre.
//Nkalem assurance 3lé remboursement ta3 traitement ta3 psy.
//Ne5ou lebsa.
//Nemchi n3adi 3lé dhahri.
//Traitement ta3 laser lel wejhi wles aisselles.
//Traitement lel Wejhi nabda fih nchallah janvier 2026.
//100 euros par mois lel tfarhid.